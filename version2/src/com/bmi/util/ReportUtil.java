package com.bmi.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

/**
 * 报告导出工具：将机构端病人分析结果导出为 Word(.docx) 报告，便于打印或发给病人。
 * 依赖 Apache POI（XWPF），无需额外依赖。
 */
public final class ReportUtil {
    private ReportUtil() {}

    /** 导出病人分析报告为 .docx。成功返回 true。 */
    public static boolean exportPatientDocx(File file, String patientCode, String gender, int age,
                                            double height, Map<String, String> metrics,
                                            List<String> risks, String analysisText,
                                            List<String[]> trend) {
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(file)) {

            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = title.createRun();
            r.setText("体质与健康分析报告");
            r.setBold(true);
            r.setFontSize(18);
            r.setFontFamily("\u5FAE\u8F6F\u96C5\u9ED1");

            XWPFParagraph info = doc.createParagraph();
            XWPFRun ri = info.createRun();
            ri.setText(String.format("病人编号：%s    性别：%s    年龄：%d 岁    身高：%.1f cm",
                    patientCode, gender, age, height));
            ri.setFontSize(11);
            ri.setFontFamily("\u5FAE\u8F6F\u96C5\u9ED1");

            doc.createParagraph();

            addHeading(doc, "\u4E00\u3001\u5173\u952E\u5065\u5EB7\u6307\u6807");
            if (metrics != null && !metrics.isEmpty()) {
                XWPFTable table = doc.createTable(metrics.size(), 2);
                int i = 0;
                for (Map.Entry<String, String> e : metrics.entrySet()) {
                    setCell(table.getRow(i).getCell(0), e.getKey());
                    setCell(table.getRow(i).getCell(1), e.getValue());
                    i++;
                }
            }

            if (risks != null && !risks.isEmpty()) {
                addHeading(doc, "\u4E8C\u3001\u98CE\u9669\u63D0\u793A");
                for (String risk : risks) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setIndentationLeft(200);
                    XWPFRun rr = p.createRun();
                    rr.setText("\u2022 " + risk);
                    rr.setFontSize(11);
                    rr.setFontFamily("\u5FAE\u8F6F\u96C5\u9ED1");
                }
            }

            addHeading(doc, "\u4E09\u3001\u5206\u6790\u4E0E\u5EFA\u8BAE");
            if (analysisText != null && !analysisText.isEmpty()) {
                for (String line : analysisText.split("\n", -1)) {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun rr = p.createRun();
                    rr.setText(line);
                    rr.setFontSize(11);
                    rr.setFontFamily("\u5FAE\u8F6F\u96C5\u9ED1");
                }
            }

            if (trend != null && !trend.isEmpty()) {
                addHeading(doc, "\u56DB\u3001\u4F53\u91CD\u8D8B\u52BF\u8BB0\u5F55");
                XWPFTable t2 = doc.createTable(trend.size() + 1, 2);
                setCell(t2.getRow(0).getCell(0), "\u8BB0\u5F55\u65F6\u95F4");
                setCell(t2.getRow(0).getCell(1), "\u4F53\u91CD(kg)");
                for (int k = 0; k < trend.size(); k++) {
                    setCell(t2.getRow(k + 1).getCell(0), trend.get(k)[0]);
                    setCell(t2.getRow(k + 1).getCell(1), trend.get(k)[1]);
                }
            }

            doc.write(out);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void addHeading(XWPFDocument doc, String text) {
        doc.createParagraph();
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(13);
        r.setFontFamily("\u5FAE\u8F6F\u96C5\u9ED1");
        r.setColor("1E6478");
    }

    private static void setCell(XWPFTableCell cell, String text) {
        cell.setText(text == null ? "" : text);
        for (XWPFParagraph p : cell.getParagraphs()) {
            for (XWPFRun run : p.getRuns()) {
                if (run != null) {
                    run.setFontFamily("\u5FAE\u8F6F\u96C5\u9ED1");
                    run.setFontSize(10);
                }
            }
        }
    }
}
