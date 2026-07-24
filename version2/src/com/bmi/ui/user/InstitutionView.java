package com.bmi.ui.user;

import com.bmi.App;
import com.bmi.db.DBUtil;
import com.bmi.util.ExcelUtil;
import com.bmi.util.HealthCalculator;
import com.bmi.util.Theme;

import javafx.geometry.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.collections.FXCollections;

import java.text.SimpleDateFormat;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** 医疗机构端 — 病人列表 + 批量导入(CSV/Excel) + 逐人分析 (机构维度病人, 与个人登录解耦) */
public class InstitutionView extends VBox {
    private final TableView<Map<String, Object>> patientTable = new TableView<>();
    private Map<String, Object> selectedProfile = null;

    public InstitutionView() {
        setSpacing(14);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        // 顶部栏
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("医疗机构工作台 — " + DBUtil.currentInstitutionName);
        title.getStyleClass().add("card-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnUpload = new Button("上传表格批量导入");
        btnUpload.getStyleClass().add("button-accent");
        Button btnNew = new Button("新建病人");
        btnNew.getStyleClass().add("button-primary");
        Button btnAnalyze = new Button("查看分析");
        btnAnalyze.getStyleClass().add("button-primary");
        Button btnBatchDel = new Button("批量移出");
        btnBatchDel.getStyleClass().add("button-ghost");
        Button btnWrite = new Button("写文章");
        btnWrite.getStyleClass().add("button-accent");
        Button btnMine = new Button("我的投稿");
        btnMine.getStyleClass().add("button-ghost");
        Button btnRefresh = new Button("刷新");
        btnRefresh.getStyleClass().add("button-ghost");
        Button btnLogout = new Button("退出");
        btnLogout.getStyleClass().add("button-ghost");
        top.getChildren().addAll(title, sp, btnUpload, btnNew, btnAnalyze, btnBatchDel, btnWrite, btnMine, btnRefresh, btnLogout);

        buildTable();
        patientTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        refresh();

        btnUpload.setOnAction(e -> uploadFile());
        btnNew.setOnAction(e -> newPatient());
        btnAnalyze.setOnAction(e -> analyzeSelected());
        btnBatchDel.setOnAction(e -> batchDelete());
        btnRefresh.setOnAction(e -> refresh());
        btnWrite.setOnAction(e -> submitArticle());
        btnMine.setOnAction(e -> showMySubmissions());
        btnLogout.setOnAction(e -> App.showLogin());

        getChildren().addAll(top, patientTable);
    }

    private void buildTable() {
        String[] cols = {"病人编号", "性别", "年龄", "身高(cm)", "最新体重(kg)", "BMI", "体质类型", "最近记录"};
        String[] keys = {"patient_code", "gender", "age", "height", "weight", "bmi", "body_type", "last_date"};
        for (int i = 0; i < cols.length; i++) {
            final int idx = i;
            TableColumn<Map<String, Object>, String> c = new TableColumn<>(cols[i]);
            c.setCellValueFactory(cb -> {
                Object v = cb.getValue().get(keys[idx]);
                return new javafx.beans.property.ReadOnlyStringWrapper(v == null ? "--" : v.toString());
            });
            patientTable.getColumns().add(c);
        }
        patientTable.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (!row.isEmpty()) selectedProfile = row.getItem();
            });
            return row;
        });
    }

    private void refresh() {
        patientTable.getItems().setAll(DBUtil.getInstitutionPatients(DBUtil.currentInstitutionId));
    }

    // ==================== 新建病人 (手动录入, 机构维度档案, 不创建登录账号) ====================

    private void newPatient() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("新建病人");
        d.setHeaderText("填写病人档案与体征, 系统将在本机构下建立病人健康档案(不创建个人登录账号)并写入一条健康记录");
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));

        TextField tfCode = new TextField(); tfCode.setPromptText("机构内病人编号/病历号 (本机构唯一)");
        ComboBox<String> cbGender = new ComboBox<>(); cbGender.getItems().addAll("男", "女"); cbGender.setValue("男");
        Spinner<Integer> spAge = new Spinner<>(1, 120, 40);
        TextField tfHeight = new TextField(); tfHeight.setPromptText("cm");
        ComboBox<String> cbAct = new ComboBox<>(); cbAct.getItems().addAll("久坐", "轻度", "中度", "重度", "极重度"); cbAct.setValue("久坐");
        TextField tfWeight = new TextField(); tfWeight.setPromptText("kg (必填)");
        TextField tfWaist = new TextField(); tfWaist.setPromptText("cm (可选)");
        TextField tfFat = new TextField(); tfFat.setPromptText("%");
        TextField tfWater = new TextField(); tfWater.setPromptText("%");
        TextField tfProtein = new TextField(); tfProtein.setPromptText("%");
        TextField tfMuscle = new TextField(); tfMuscle.setPromptText("%");
        TextField tfVisc = new TextField(); tfVisc.setPromptText("0-30");
        TextField tfBoneM = new TextField(); tfBoneM.setPromptText("kg");
        TextField tfBone = new TextField(); tfBone.setPromptText("kg");
        DatePicker dpDate = new DatePicker(); dpDate.setValue(java.time.LocalDate.now());

        int r = 0;
        g.add(new Label("病人编号*:"), 0, r); g.add(tfCode, 1, r++);
        g.add(new Label("性别:"), 0, r); g.add(cbGender, 1, r++);
        g.add(new Label("年龄:"), 0, r); g.add(spAge, 1, r++);
        g.add(new Label("身高(cm):"), 0, r); g.add(tfHeight, 1, r++);
        g.add(new Label("活动等级:"), 0, r); g.add(cbAct, 1, r++);
        g.add(new Label("体重(kg)*:"), 0, r); g.add(tfWeight, 1, r++);
        g.add(new Label("腰围(cm):"), 0, r); g.add(tfWaist, 1, r++);
        g.add(new Label("体脂率(%):"), 0, r); g.add(tfFat, 1, r++);
        g.add(new Label("水分率(%):"), 0, r); g.add(tfWater, 1, r++);
        g.add(new Label("蛋白质率(%):"), 0, r); g.add(tfProtein, 1, r++);
        g.add(new Label("肌肉率(%):"), 0, r); g.add(tfMuscle, 1, r++);
        g.add(new Label("内脏脂肪:"), 0, r); g.add(tfVisc, 1, r++);
        g.add(new Label("骨骼肌(kg):"), 0, r); g.add(tfBoneM, 1, r++);
        g.add(new Label("骨量(kg):"), 0, r); g.add(tfBone, 1, r++);
        g.add(new Label("记录日期:"), 0, r); g.add(dpDate, 1, r++);

        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        d.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String code = tfCode.getText() == null ? "" : tfCode.getText().trim();
            if (code.isEmpty()) { alert("病人编号不能为空"); return; }
            double weight = num(tfWeight);
            if (weight <= 0) { alert("体重必须填写且大于 0"); return; }
            double height = num(tfHeight);
            if (height < 50 || height > 300) { alert("身高范围 50-300 cm"); return; }
            int age = spAge.getValue();
            Double waist = tfWaist.getText().trim().isEmpty() ? null : num(tfWaist);
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("body_fat", num(tfFat));
            metrics.put("water_rate", num(tfWater));
            metrics.put("protein_rate", num(tfProtein));
            metrics.put("muscle_rate", num(tfMuscle));
            metrics.put("visceral_fat", num(tfVisc));
            metrics.put("bone_muscle", num(tfBoneM));
            metrics.put("bone_mass", num(tfBone));
            if (dpDate.getValue() != null)
                metrics.put("record_date", java.util.Date.from(dpDate.getValue().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));

            boolean ok = DBUtil.addInstitutionPatient(DBUtil.currentInstitutionId, code,
                    cbGender.getValue(), age, height, cbAct.getValue(), weight, waist, metrics);
            if (ok) {
                alert("新建病人成功: " + code);
                refresh();
            } else {
                alert("创建失败 (该编号可能已存在或数据库异常)");
            }
        });
    }

    // ==================== 批量移出病人 (软删除, 保留全部健康记录) ====================

    private void batchDelete() {
        List<Map<String, Object>> sels = patientTable.getSelectionModel().getSelectedItems();
        if (sels == null || sels.isEmpty()) { alert("请先勾选(多选)要移出的病人"); return; }
        List<String> codes = new ArrayList<>();
        for (Map<String, Object> m : sels) {
            Object c = m.get("patient_code");
            if (c != null) codes.add(c.toString());
        }
        if (codes.isEmpty()) return;
        Alert cf = new Alert(Alert.AlertType.CONFIRMATION,
                "确认将选中的 " + codes.size() + " 个病人移出本机构名单？\n(仅解除归属/软删除, 保留其全部健康记录)", ButtonType.OK, ButtonType.CANCEL);
        cf.setTitle("批量移出确认");
        cf.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            int n = DBUtil.removeInstitutionPatients(DBUtil.currentInstitutionId, codes);
            alert("已移出 " + n + " 个病人");
            refresh();
        });
    }

    private double num(TextField tf) {
        String s = tf.getText() == null ? "" : tf.getText().trim();
        if (s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private void uploadFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择病人健康数据表格 (CSV / Excel)");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("表格文件", "*.csv", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("CSV 文件", "*.csv"),
                new FileChooser.ExtensionFilter("Excel 文件", "*.xlsx", "*.xls"));
        File file = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;
        List<Map<String, Object>> rows;
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".csv")) {
                rows = parseCsv(file);
            } else {
                rows = ExcelUtil.readHealthRecords(file);
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            alert("读取文件失败: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return;
        }
        if (rows == null || rows.isEmpty()) {
            alert("文件中没有可导入的数据行\n(表头需含: 病人编号/patient_code, 体重/weight, 体脂率/body_fat ...\n可选: 性别/年龄/身高/活动水平)");
            return;
        }
        DBUtil.ImportResult res = DBUtil.importInstitutionRecords(DBUtil.currentInstitutionId, rows);
        StringBuilder sb = new StringBuilder();
        sb.append("导入完成: 成功 ").append(res.success).append(" 条");
        if (!res.errors.isEmpty()) {
            sb.append("\n跳过 ").append(res.errors.size()).append(" 条:\n");
            for (String err : res.errors) sb.append("  - ").append(err).append("\n");
        }
        alert(sb.toString());
        refresh();
    }

    private List<Map<String, Object>> parseCsv(File file) {
        List<Map<String, Object>> rows = new ArrayList<>();
        SimpleDateFormat[] fmts = {new SimpleDateFormat("yyyy-MM-dd"), new SimpleDateFormat("yyyy/MM/dd")};
        try (BufferedReader br = Files.newBufferedReader(file.toPath())) {
            String header = br.readLine();
            if (header == null) return rows;
            String[] headers = header.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] vals = line.split(",");
                if (vals.length < headers.length) continue;
                Map<String, Object> m = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].trim().toLowerCase();
                    String v = vals[i].trim();
                    if (h.contains("病人编号") || h.contains("病历号") || h.equals("patient_code")
                            || h.equals("username") || h.equals("用户名") || h.contains("姓名")) {
                        m.put("patient_code", v);
                    } else if ("record_date".equals(h) || h.contains("日期")) {
                        Date d = null;
                        for (SimpleDateFormat f : fmts) {
                            try { d = f.parse(v); break; } catch (Exception ignore) {}
                        }
                        if (d != null) m.put("record_date", d);
                    } else if (h.contains("性别") || h.equals("gender")) {
                        m.put("gender", v.isEmpty() ? null : v);
                    } else if (h.contains("年龄") || h.equals("age")) {
                        try { m.put("age", Double.parseDouble(v)); } catch (Exception ignore) {}
                    } else if (h.contains("身高") || h.equals("height")) {
                        try { m.put("height", Double.parseDouble(v)); } catch (Exception ignore) {}
                    } else if (h.contains("活动") || h.equals("activity")) {
                        m.put("activity", v.isEmpty() ? null : v);
                    } else {
                        try { m.put(h, Double.parseDouble(v)); } catch (Exception ignore) { m.put(h, 0.0); }
                    }
                }
                if (m.get("patient_code") != null) rows.add(m);
            }
        } catch (Exception e) {
            DBUtil.logError("InstitutionView.parseCsv", e);
        }
        return rows;
    }

    private void analyzeSelected() {
        Map<String, Object> row = patientTable.getSelectionModel().getSelectedItem();
        if (row == null) { alert("请先选择一位病人"); return; }
        Object idObj = row.get("id");
        if (!(idObj instanceof Number)) { alert("该病人缺少有效标识"); return; }
        int patientId = ((Number) idObj).intValue();
        String code = (String) row.get("patient_code");
        String gender = (String) row.get("gender");
        int age = ((Number) row.get("age")).intValue();
        double height = ((Number) row.get("height")).doubleValue();
        Map<String, Object> rec = DBUtil.getLatestHealthRecord(patientId);
        if (rec == null) { alert("该病人暂无健康记录"); return; }

        List<Map<String, Object>> history = DBUtil.getHealthRecords(patientId, 200);
        showAnalysisDialog(code, gender, age, height, rec, history);
    }

    private void showAnalysisDialog(String patientCode, String gender, int age, double height,
                                    Map<String, Object> rec, List<Map<String, Object>> history) {
        double weight = ((Number) rec.get("weight")).doubleValue();
        double bmi = HealthCalculator.calcBMI(weight, height);
        double bodyFat = ((Number) rec.get("body_fat")).doubleValue();
        int visceral = ((Number) rec.get("visceral_fat")).intValue();
        double muscleRate = ((Number) rec.get("muscle_rate")).doubleValue();
        double waterRate = ((Number) rec.get("water_rate")).doubleValue();
        double waist = ((Number) rec.get("waist")).doubleValue();
        double boneMuscle = ((Number) rec.get("bone_muscle")).doubleValue();

        int healthScore = HealthCalculator.calcHealthScore(bmi, bodyFat, visceral, muscleRate, waterRate, gender);
        String scoreLevel = HealthCalculator.scoreLevel(healthScore);

        VBox root = new VBox(14);
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: transparent;");

        // —— 顶部: 标题 + 综合评分 ——
        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label("病人分析 — " + patientCode + "（" + gender + "，" + age + "岁）");
        t.getStyleClass().add("card-title");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label score = new Label("健康评分 " + healthScore + " / 100  ·  " + scoreLevel);
        score.getStyleClass().add("chip");
        head.getChildren().addAll(t, sp, score);

        // —— 关键指标网格 ——
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);
        String[][] items = {
                {"BMI", String.format("%.1f", bmi) + " (" + HealthCalculator.classifyBMI(bmi) + ")"},
                {"体脂率", String.format("%.1f", bodyFat) + "%"},
                {"内脏脂肪", visceral + " (" + HealthCalculator.assessVisceralFat(visceral) + ")"},
                {"肌肉量评级", HealthCalculator.assessMuscle(boneMuscle, weight, gender)},
                {"水分率", String.format("%.1f", waterRate) + "%"},
                {"腰围", String.format("%.1f", waist) + " cm"},
                {"腰高比", HealthCalculator.assessWHtR(waist, height)},
                {"体质类型", String.valueOf(rec.get("body_type"))},
        };
        for (int i = 0; i < items.length; i++) {
            Label k = new Label(items[i][0]); k.getStyleClass().add("muted");
            Label v = new Label(items[i][1]); v.getStyleClass().add("metric");
            grid.add(k, (i % 2) * 2, i / 2);
            grid.add(v, (i % 2) * 2 + 1, i / 2);
        }

        // —— 风险标记 ——
        List<String> risks = buildRiskFlags(gender, age, bmi, bodyFat, visceral, muscleRate, waist, height);

        // —— 趋势图 ——
        LineChart<String, Number> chart = buildTrendChart(history);

        // —— 智能建议 + 趋势预测 ——
        Map<String, Object> rec2 = new HashMap<>(rec);
        rec2.put("username", patientCode);
        Map<String, Object> rcm = HealthCalculator.recommendGoal(rec2, gender, age, height);

        StringBuilder sb = new StringBuilder();
        sb.append("【智能建议】\n").append(rcm.get("reason")).append("\n");
        sb.append("推荐目标: ").append(rcm.get("goalType")).append(" / ")
                .append(String.format("%.1f", (double) rcm.get("targetWeight"))).append(" kg\n\n");

        if (history.size() >= 3) {
            List<Date> dates = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Map<String, Object> m : history) {
                dates.add((Date) m.get("record_date"));
                weights.add(((Number) m.get("weight")).doubleValue());
            }
            double predW = HealthCalculator.predictTrend(dates, weights, 30);
            double predBMI = HealthCalculator.calcBMI(predW, height);
            sb.append("【趋势预测】\n");
            sb.append("体重趋势: ").append(HealthCalculator.trendDirection(dates, weights)).append("\n");
            sb.append("30天后预测体重: ").append(Double.isNaN(predW) ? "数据不足" : String.format("%.1f", predW) + " kg")
                    .append(" (预测BMI ").append(String.format("%.1f", predBMI)).append(")\n");
            sb.append(HealthCalculator.assessRisk(predBMI)).append("\n");
        } else {
            sb.append("【趋势预测】\n记录不足3条, 暂无法预测趋势, 建议该病人持续打卡。\n");
        }
        if (!risks.isEmpty()) {
            sb.append("\n【风险提示】\n");
            for (String r : risks) sb.append("  ? ").append(r).append("\n");
        }

        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false); ta.setWrapText(true); ta.setPrefSize(560, 200);

        root.getChildren().addAll(head, grid, chart, ta);

        Dialog<Void> d = new Dialog<>();
        d.setTitle("病人分析 — " + patientCode);
        d.setHeaderText(null);
        d.getDialogPane().setContent(root);
        d.getDialogPane().setPrefSize(620, 640);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }

    private List<String> buildRiskFlags(String gender, int age, double bmi, double bodyFat,
                                        int visceral, double muscleRate, double waist, double height) {
        List<String> risks = new ArrayList<>();
        boolean male = "男".equals(gender);
        if (bmi >= 28) risks.add("BMI 已达肥胖区间 (" + String.format("%.1f", bmi) + ")，心血管与代谢疾病风险升高");
        else if (bmi >= 24) risks.add("BMI 超重 (" + String.format("%.1f", bmi) + ")，建议控制体重");
        else if (bmi < 18.5) risks.add("BMI 偏瘦 (" + String.format("%.1f", bmi) + ")，需关注营养摄入");
        double fatHigh = male ? 25 : 32;
        double fatLow = male ? 12 : 20;
        if (bodyFat > fatHigh) risks.add("体脂率过高 (" + String.format("%.1f", bodyFat) + "%)，建议减脂");
        else if (bodyFat < fatLow) risks.add("体脂率偏低 (" + String.format("%.1f", bodyFat) + "%)");
        if (visceral > 8) risks.add("内脏脂肪过高 (" + visceral + ")，中心性肥胖，代谢综合征风险");
        double stdMuscle = male ? 40.0 : 35.0;
        if (muscleRate > 0 && muscleRate < stdMuscle * 0.9) risks.add("肌肉量偏低 (" + String.format("%.1f", muscleRate) + "%)");
        if (waist > 0) {
            String whr = HealthCalculator.assessWHtR(waist, height);
            if (whr.startsWith("偏高") || whr.startsWith("高")) risks.add("腰高比" + whr + "，腹部脂肪堆积");
        }
        int bodyAge = HealthCalculator.calcBodyAge(age, bodyFat, muscleRate, visceral, gender);
        if (bodyAge - age >= 3) risks.add("身体年龄 " + bodyAge + " 岁，比实际年龄大 " + (bodyAge - age) + " 岁");
        return risks;
    }

    private LineChart<String, Number> buildTrendChart(List<Map<String, Object>> history) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        x.setLabel("记录时间"); y.setLabel("数值");
        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setTitle("健康指标趋势 (" + history.size() + " 条记录)");
        chart.setPrefSize(580, 240);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);

        XYChart.Series<String, Number> sWeight = new XYChart.Series<>();
        sWeight.setName("体重(kg)");
        XYChart.Series<String, Number> sBmi = new XYChart.Series<>();
        sBmi.setName("BMI");
        XYChart.Series<String, Number> sFat = new XYChart.Series<>();
        sFat.setName("体脂率(%)");

        Set<String> seen = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
        for (Map<String, Object> m : history) {
            Date d = (Date) m.get("record_date");
            String label = sdf.format(d);
            while (seen.contains(label)) label += "*";
            seen.add(label);
            sWeight.getData().add(new XYChart.Data<>(label, ((Number) m.get("weight")).doubleValue()));
            sBmi.getData().add(new XYChart.Data<>(label, ((Number) m.get("bmi")).doubleValue()));
            sFat.getData().add(new XYChart.Data<>(label, ((Number) m.get("body_fat")).doubleValue()));
        }
        chart.getData().addAll(sWeight, sBmi, sFat);
        return chart;
    }

    /** 机构写文章：保存为待审核投稿, author=机构名, author_type=institution */
    private void submitArticle() {
        TextField tfTitle = new TextField();
        TextField tfCategory = new TextField("健康科普");
        TextArea ta = new TextArea();
        ta.setPrefRowCount(6); ta.setWrapText(true);
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8);
        g.addRow(0, new Label("标题"), tfTitle);
        g.addRow(1, new Label("分类"), tfCategory);
        g.add(new Label("内容"), 0, 2);
        g.add(ta, 1, 2);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("机构写文章");
        d.setHeaderText("提交后由管理员审核，通过才会公开发布");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String t = tfTitle.getText().trim();
            if (t.isEmpty()) { alert("标题不能为空"); return; }
            if (DBUtil.saveHealthArticle(0, t, ta.getText(), tfCategory.getText().trim(),
                    DBUtil.currentInstitutionName, "institution", "待审核")) {
                alert("投稿已提交，等待管理员审核");
            } else alert("提交失败");
        });
    }

    /** 我的投稿：查看 + 编辑本机构投稿文章（已发布文章再编辑需重新审核） */
    private void showMySubmissions() {
        List<String[]> rows = DBUtil.getMyArticles(DBUtil.currentInstitutionName, "institution");
        TableView<String[]> t = new TableView<>();
        t.getColumns().addAll(
                colA("ID", 0, 60), colA("标题", 1, 240), colA("分类", 2, 110),
                colA("状态", 3, 90), colA("提交时间", 4, 150));
        t.setItems(FXCollections.observableArrayList(rows));

        Button btnEdit = new Button("查看 / 编辑");
        btnEdit.getStyleClass().add("button-primary");
        btnEdit.setDisable(true);
        t.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> btnEdit.setDisable(n == null));
        btnEdit.setOnAction(e -> {
            String[] sel = t.getSelectionModel().getSelectedItem();
            if (sel != null) editArticle(Integer.parseInt(sel[0]), sel[3]);
        });
        // 双击行也可直接编辑
        t.setRowFactory(tv -> {
            TableRow<String[]> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (!row.isEmpty() && ev.getClickCount() == 2) {
                    editArticle(Integer.parseInt(row.getItem()[0]), row.getItem()[3]);
                }
            });
            return row;
        });

        Label tip = new Label("本机构投稿（状态：待审核/已发布/已驳回，选中后点「查看/编辑」或双击行可编辑）");
        tip.getStyleClass().add("muted");
        VBox box = new VBox(10, tip, t, btnEdit);
        box.setPadding(new Insets(8));
        Alert d = new Alert(Alert.AlertType.INFORMATION);
        d.setTitle("我的投稿");
        d.setHeaderText(null);
        d.getDialogPane().setContent(box);
        d.setResizable(true);
        d.showAndWait();
    }

    /** 编辑本机构已投稿文章；已发布文章再次提交会自动退回「待审核」重新进入审核流程 */
    private void editArticle(int id, String currentStatus) {
        Map<String, String> art = DBUtil.getHealthArticleById(id);
        if (art == null || art.isEmpty()) { alert("文章不存在或已被删除"); return; }
        boolean published = "已发布".equals(art.get("status"));

        TextField tfTitle = new TextField(art.get("title"));
        TextField tfCategory = new TextField(art.get("category"));
        TextArea ta = new TextArea(art.get("content"));
        ta.setPrefRowCount(10); ta.setWrapText(true);

        Label lbStatus = new Label("当前状态：" + art.get("status")
                + (published ? "（编辑后需重新审核）" : ""));
        lbStatus.getStyleClass().add("chip");

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8);
        g.addRow(0, new Label("标题"), tfTitle);
        g.addRow(1, new Label("分类"), tfCategory);
        g.add(new Label("内容"), 0, 2);
        g.add(ta, 1, 2);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("编辑文章 — " + art.get("title"));
        d.setHeaderText(null);
        VBox dlgBody = new VBox(8, lbStatus);
        if ("已驳回".equals(art.get("status"))) {
            String reject = art.get("reject_reason");
            Label lbReject = new Label("驳回理由：" + (reject == null || reject.isEmpty() ? "（未填写）" : reject));
            lbReject.setStyle("-fx-text-fill:#C0392B; -fx-font-weight:bold;");
            lbReject.setWrapText(true);
            dlgBody.getChildren().add(lbReject);
        }
        dlgBody.getChildren().add(g);
        d.getDialogPane().setContent(dlgBody);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResizable(true);
        d.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String t = tfTitle.getText().trim();
            if (t.isEmpty()) { alert("标题不能为空"); return; }
            // 已发布文章再编辑 -> 重新进入审核(待审核)；其余情形保持待审核
            String saveStatus = published ? "待审核" : art.get("status");
            boolean ok = DBUtil.saveHealthArticle(id, t, ta.getText(), tfCategory.getText().trim(),
                    DBUtil.currentInstitutionName, "institution", saveStatus);
            if (ok) {
                alert(published
                        ? "已保存，文章已退回「待审核」，等待管理员重新审核。"
                        : "已保存，等待管理员审核。");
            } else alert("保存失败");
        });
    }

    private TableColumn<String[], String> colA(String name, int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>(name);
        c.setCellValueFactory(cb -> new javafx.beans.property.ReadOnlyStringWrapper(cb.getValue()[idx]));
        c.setPrefWidth(w);
        return c;
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }

    public VBox getRoot() { return this; }
}
