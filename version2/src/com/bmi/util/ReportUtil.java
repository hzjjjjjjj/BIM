package com.bmi.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * 报告导出工具：将机构端病人分析结果导出为 Word(.docx) 报告，便于打印或发给病人。
 *
 * 说明：采用纯 java.util.zip + 手写 OOXML 生成 .docx，不依赖 Apache POI 的写入路径，
 * 因此不受 commons-compress 版本（POI 写 zip 所需的 putArchiveEntry 重载）影响，
 * 导出功能与 Excel 导入互不干扰。
 */
public final class ReportUtil {
    private ReportUtil() {}

    /** 导出病人分析报告为 .docx。成功返回 true。 */
    public static boolean exportPatientDocx(File file, String patientCode, String gender, int age,
                                            double height, Map<String, String> metrics,
                                            List<String> risks, String analysisText,
                                            List<String[]> trend) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            writeEntry(zos, "[Content_Types].xml", CONTENT_TYPES);
            writeEntry(zos, "_rels/.rels", RELS_ROOT);
            writeEntry(zos, "word/document.xml",
                    documentXml(patientCode, gender, age, height, metrics, risks, analysisText, trend));
            writeEntry(zos, "word/_rels/document.xml.rels", RELS_DOCUMENT);
            writeEntry(zos, "word/styles.xml", STYLES);
            writeEntry(zos, "docProps/core.xml", CORE);
            writeEntry(zos, "docProps/app.xml", APP);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zos.putNextEntry(e);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String documentXml(String patientCode, String gender, int age, double height,
                                       Map<String, String> metrics, List<String> risks,
                                       String analysisText, List<String[]> trend) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        b.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n<w:body>\n");
        b.append(para("\u4f53\u8d28\u4e0e\u5065\u5eb7\u5206\u6790\u62a5\u544a", true, 36, "1E6478", true, false));
        b.append(para(String.format("\u75c5\u4eba\u7f16\u53f7\uff1a%s    \u6027\u522b\uff1a%s    \u5e74\u9f84\uff1a%d \u5c81    \u8eab\u9ad8\uff1a%.1f cm",
                patientCode, gender, age, height), false, 0, null, false, false));
        b.append(para("\u4e00\u3001\u5173\u952e\u5065\u5eb7\u6307\u6807", false, 0, null, false, true));
        if (metrics != null && !metrics.isEmpty()) {
            List<String[]> rows = new ArrayList<>();
            for (Map.Entry<String, String> en : metrics.entrySet()) rows.add(new String[]{en.getKey(), en.getValue()});
            b.append(table(rows));
        }
        if (risks != null && !risks.isEmpty()) {
            b.append(para("\u4e8c\u3001\u98ce\u9669\u63d0\u793a", false, 0, null, false, true));
            for (String r : risks) b.append(para("\u2022 " + r, false, 0, null, false, false));
        }
        b.append(para("\u4e09\u3001\u5206\u6790\u4e0e\u5efa\u8bae", false, 0, null, false, true));
        if (analysisText != null && !analysisText.isEmpty()) {
            for (String line : analysisText.split("\n", -1)) b.append(para(line, false, 0, null, false, false));
        }
        if (trend != null && !trend.isEmpty()) {
            b.append(para("\u56db\u3001\u4f53\u91cd\u8d8b\u52bf\u8bb0\u5f55", false, 0, null, false, true));
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"\u8bb0\u5f55\u65f6\u95f4", "\u4f53\u91cd(kg)"});
            rows.addAll(trend);
            b.append(table(rows));
        }
        b.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>\n");
        b.append("</w:body>\n</w:document>");
        return b.toString();
    }

    /** 生成段落。参数：文本/加粗/半磅字号(0=默认)/颜色(null=默认)/居中/使用标题样式 */
    private static String para(String text, boolean bold, int halfPt, String color,
                               boolean center, boolean heading) {
        StringBuilder b = new StringBuilder("<w:p>");
        b.append("<w:pPr>");
        if (heading) b.append("<w:pStyle w:val=\"Heading1\"/>");
        if (center) b.append("<w:jc w:val=\"center\"/>");
        b.append("</w:pPr>");
        b.append("<w:r>");
        if (bold || halfPt > 0 || color != null) {
            b.append("<w:rPr>");
            if (bold) b.append("<w:b/>");
            if (halfPt > 0) b.append("<w:sz w:val=\"").append(halfPt).append("\"/>");
            if (color != null) b.append("<w:color w:val=\"").append(color).append("\"/>");
            b.append("</w:rPr>");
        }
        b.append("<w:t xml:space=\"preserve\">").append(esc(text)).append("</w:t>");
        b.append("</w:r></w:p>");
        return b.toString();
    }

    /** 生成 N 列表格（等宽列，带边框） */
    private static String table(List<String[]> rows) {
        int cols = 0;
        for (String[] r : rows) cols = Math.max(cols, r.length);
        if (cols == 0) cols = 2;
        int colW = 9000 / cols;
        StringBuilder b = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/>");
        b.append("<w:tblBorders>")
         .append("<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
         .append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
         .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
         .append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
         .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
         .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
         .append("</w:tblBorders></w:tblPr><w:tblGrid>");
        for (int i = 0; i < cols; i++) b.append("<w:gridCol w:w=\"").append(colW).append("\"/>");
        b.append("</w:tblGrid>");
        for (String[] r : rows) {
            b.append("<w:tr>");
            for (int c = 0; c < cols; c++) {
                String val = c < r.length ? r[c] : "";
                b.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(colW).append("\" w:type=\"dxa\"/></w:tcPr>")
                 .append("<w:p>").append("<w:r><w:t xml:space=\"preserve\">").append(esc(val)).append("</w:t></w:r>").append("</w:p>")
                 .append("</w:tc>");
            }
            b.append("</w:tr>");
        }
        b.append("</w:tbl>");
        return b.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    private static final String CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
        "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
        "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
        "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
        "  <Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>\n" +
        "  <Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>\n" +
        "  <Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>\n" +
        "</Types>";

    private static final String RELS_ROOT =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
        "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
        "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>\n" +
        "  <Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>\n" +
        "</Relationships>";

    private static final String RELS_DOCUMENT =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
        "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n" +
        "</Relationships>";

    private static final String STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
        "  <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Microsoft YaHei\" w:eastAsia=\"Microsoft YaHei\" w:hAnsi=\"Microsoft YaHei\"/></w:rPr></w:rPrDefault></w:docDefaults>\n" +
        "  <w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>\n" +
        "  <w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"26\"/><w:color w:val=\"1E6478\"/></w:rPr></w:style>\n" +
        "</w:styles>";

    private static final String CORE =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\">\n" +
        "  <dc:title>\u4f53\u8d28\u4e0e\u5065\u5eb7\u5206\u6790\u62a5\u544a</dc:title>\n" +
        "  <cp:lastModifiedBy>BMI\u4f53\u8d28\u8bc4\u4f30\u4e0e\u9884\u6d4b\u7cfb\u7edf</cp:lastModifiedBy>\n" +
        "</cp:coreProperties>";

    private static final String APP =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">\n" +
        "  <Application>BMI\u4f53\u8d28\u8bc4\u4f30\u4e0e\u9884\u6d4b\u7cfb\u7edf</Application>\n" +
        "</Properties>";
}
