package com.bmi.util;

import com.bmi.db.DBUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

/**
 * 轻量级邮件发送工具 —— 基于原生 Socket 实现 SMTP 协议（支持 STARTTLS 与直接 SSL），
 * 不依赖任何第三方邮件库，保证项目可离线编译运行。
 *
 * 配置读取 mail.properties（位于程序工作目录，或 classpath 根目录）：
 *   mail.enabled=true                是否启用邮件发送（默认 false，未配置时不发送、不影响主流程）
 *   mail.smtp.host=smtp.example.com  SMTP 服务器地址
 *   mail.smtp.port=587              端口（587 配 useTLS=true；465 配 useTLS=false 走 SSL）
 *   mail.smtp.user=xxx@example.com  登录账号
 *   mail.smtp.pass=授权码            邮箱授权码（非登录密码）
 *   mail.from=xxx@example.com       发件人（默认同 user）
 *   mail.useTLS=true                 true=STARTTLS(587)；端口 465 请设为 false
 *   mail.timeout=10000               Socket 超时（毫秒）
 *
 * 设计原则：任何异常都被捕获并以日志记录，返回 false，绝不影响调用方主流程
 * （例如机构审批通过后即使邮件发送失败，审批结果仍然有效）。
 */
public class MailUtil {

    private static final Properties CFG = loadConfig();

    private static Properties loadConfig() {
        Properties p = new Properties();
        // 优先从工作目录读取，其次从 classpath 根读取
        File f = new File("mail.properties");
        try (InputStream in = f.exists() ? new FileInputStream(f)
                : MailUtil.class.getResourceAsStream("/mail.properties")) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignore) {
            // 配置缺失则使用默认值，邮件功能保持关闭
        }
        return p;
    }

    private static boolean isEnabled() {
        return "true".equalsIgnoreCase(CFG.getProperty("mail.enabled", "false"));
    }

    /**
     * 发送「机构入驻审批通过」通知邮件（含机构编号）。
     * @return 是否成功发送（未启用 / 收件人为空 / 发送失败均返回 false，不抛异常）
     */
    public static boolean sendInstitutionCodeEmail(String to, String orgName, String code) {
        // 收件人非空由机构申请表单(必填 + 邮箱格式校验)保证, 此处不再重复校验
        if (!isEnabled()) {
            System.out.println("[Mail] 邮件服务未启用 (mail.enabled=false)，跳过发送；收件人=" + to);
            return false;
        }
        String subject = "【BMI 系统】您的医疗机构入驻申请已通过";
        String body = "尊敬的管理员：\n\n"
                + "您提交的医疗机构「" + (orgName == null ? "" : orgName) + "」入驻申请已审核通过。\n"
                + "请使用以下机构编号登录本系统：\n\n"
                + "    机构编号：" + (code == null ? "" : code) + "\n\n"
                + "登录方式：机构编号 + 您申请时自行设置的密码。\n"
                + "如未收到本邮件或对机构编号有疑问，请联系平台管理员。\n\n"
                + "—— BMI 体质评估与预测系统";
        return sendMail(to.trim(), subject, body);
    }

    /** 通用 SMTP 发送（已做编码与异常处理）。返回是否发送成功。 */
    public static boolean sendMail(String to, String subject, String text) {
        String host = CFG.getProperty("mail.smtp.host", "").trim();
        String portStr = CFG.getProperty("mail.smtp.port", "25").trim();
        String user = CFG.getProperty("mail.smtp.user", "").trim();
        String pass = CFG.getProperty("mail.smtp.pass", "").trim();
        String from = CFG.getProperty("mail.from", user).trim();
        boolean useTLS = "true".equalsIgnoreCase(CFG.getProperty("mail.useTLS", "true"));
        int timeout;
        try { timeout = Integer.parseInt(CFG.getProperty("mail.timeout", "10000").trim()); }
        catch (NumberFormatException e) { timeout = 10000; }
        int port;
        try { port = Integer.parseInt(portStr); } catch (NumberFormatException e) { port = 25; }

        if (host.isEmpty() || port <= 0) {
            DBUtil.logError("MailUtil.sendMail", new IllegalStateException("SMTP host/port 未配置"));
            return false;
        }

        Socket socket = null;
        try {
            javax.net.ssl.SSLSocketFactory sslFactory =
                    (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            boolean ssl = !useTLS && port == 465; // 465 直接 SSL；其余明文 + 可选 STARTTLS
            if (ssl) {
                socket = sslFactory.createSocket(host, port);
            } else {
                socket = new Socket(host, port);
            }
            socket.setSoTimeout(timeout);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            expect(reader, "220");
            send(writer, "EHLO localhost");
            skipMultiline(reader);

            if (useTLS && !ssl) {
                send(writer, "STARTTLS");
                expect(reader, "220");
                socket = sslFactory.createSocket(socket, host, port, true);
                reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                send(writer, "EHLO localhost");
                skipMultiline(reader);
            }

            if (!user.isEmpty()) {
                send(writer, "AUTH LOGIN");
                expect(reader, "334");
                send(writer, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
                expect(reader, "334");
                send(writer, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
                expect(reader, "235");
            }

            send(writer, "MAIL FROM:<" + from + ">");
            expect(reader, "250");
            send(writer, "RCPT TO:<" + to + ">");
            expect(reader, "250");
            send(writer, "DATA");
            expect(reader, "354");

            // 发件人显示名: 收件人看到 "BMI体质评估系统" 而非裸邮箱;
            // 显示名经 RFC2047(Base64) 编码以支持中文, 留空则只显示邮箱
            String displayName = CFG.getProperty("mail.from.name", "BMI体质评估系统").trim();
            String fromHeader = from;
            if (!displayName.isEmpty()) {
                String encName = "=?UTF-8?B?"
                        + Base64.getEncoder().encodeToString(displayName.getBytes(StandardCharsets.UTF_8)) + "?=";
                fromHeader = encName + " <" + from + ">";
            }
            writer.println("From: " + fromHeader);
            writer.println("To: " + to);
            writer.println("Subject: =?UTF-8?B?"
                    + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=");
            writer.println("MIME-Version: 1.0");
            writer.println("Content-Type: text/plain; charset=UTF-8");
            writer.println("Content-Transfer-Encoding: base64");
            writer.println();
            String b64 = Base64.getMimeEncoder(76, new byte[]{'\r', '\n'})
                    .encodeToString(text.getBytes(StandardCharsets.UTF_8));
            writer.println(b64);
            writer.println(".");
            expect(reader, "250");

            send(writer, "QUIT");
            return true;
        } catch (Exception e) {
            DBUtil.logError("MailUtil.sendMail", e);
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignore) {}
            }
        }
    }

    private static void send(PrintWriter w, String s) {
        w.println(s);
        w.flush();
    }

    private static void expect(BufferedReader r, String code) throws IOException {
        String line = r.readLine();
        if (line == null || !line.startsWith(code)) {
            throw new IOException("SMTP 响应异常, 期望 " + code + " 实际收到: " + line);
        }
    }

    /** 读取 EHLO 的多行响应（形如 "250-..." 续行，以 "250 " 结束） */
    private static void skipMultiline(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
        }
    }
}
