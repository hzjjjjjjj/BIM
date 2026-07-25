package com.bmi.util;

import com.bmi.util.MailUtil;

public class MailTest {
    public static void main(String[] args) {
        String to = "radiance_55@qq.com";
        boolean ok = MailUtil.sendMail(to,
                "BMI Mail Config Test",
                "Hello, this is a test email from BMI system.\nIf you received it, QQ SMTP is working.");
        System.out.println(ok ? "[OK] sent, please check " + to
                              : "[FAIL] send failed, see bmi_error.log");
    }
}
