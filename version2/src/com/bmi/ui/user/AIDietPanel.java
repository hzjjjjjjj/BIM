package com.bmi.ui.user;

import com.bmi.db.DBUtil;
import com.bmi.util.AllergenKB;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;

import java.util.*;
import java.util.Map;

/** AI 饮食推荐面板 — 根据用户目标生成饮食方案 */
public class AIDietPanel extends VBox {
    private final ComboBox<String> cbGoal = new ComboBox<>();
    private final TextField tfCustom = new TextField();
    private final TextField tfApiKey = new TextField();
    private final TextArea taPlan = new TextArea();
    private final ListView<String> historyList = new ListView<>();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final Map<Integer, String[]> historyMap = new HashMap<>();

    public AIDietPanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        cbGoal.getItems().addAll("维持体重", "减脂", "增肌", "控糖");
        cbGoal.setValue("减脂");
        tfCustom.setPrefWidth(260);

        HBox input = new HBox(10);
        input.setAlignment(Pos.CENTER_LEFT);
        Label lg = new Label("饮食目标:"); lg.getStyleClass().add("sub-title");
        Label lc = new Label("或自定义需求:"); lc.getStyleClass().add("sub-title");
        Label lk = new Label("API Key (可选):"); lk.getStyleClass().add("sub-title");
        input.getChildren().addAll(lg, cbGoal, lc, tfCustom, lk, tfApiKey);

        Button btnGen = new Button("生成推荐方案");
        btnGen.getStyleClass().add("button-primary");
        HBox btnRow = new HBox(btnGen);
        btnRow.setAlignment(Pos.CENTER);

        VBox topCard = new VBox(10);
        topCard.getStyleClass().add("card");
        Label t0 = new Label("AI 饮食推荐");
        t0.getStyleClass().add("card-title");
        topCard.getChildren().addAll(t0, input, btnRow);

        taPlan.setEditable(false);
        taPlan.setWrapText(true);
        taPlan.setPrefHeight(360);
        taPlan.setStyle("-fx-font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif; -fx-font-size: 13px;");
        taPlan.setText("选择饮食目标或直接在「自定义需求」中输入你的饮食问题，例如:\n" +
                "\"我有高血压，晚餐吃什么好？\"\n\"健身后需要补充什么？\"\n\"给我一份一周低碳食谱\"\n\n" +
                "输入 API Key 可调用大模型生成更个性化方案；留空则使用本地推荐模板。");
        VBox centerCard = new VBox(10);
        centerCard.getStyleClass().add("card");
        Label t1 = new Label("推荐方案");
        t1.getStyleClass().add("card-title");
        centerCard.getChildren().addAll(t1, new ScrollPane(taPlan){{setFitToWidth(true);}});

        VBox leftCard = new VBox(10);
        leftCard.getStyleClass().add("card");
        Label t2 = new Label("历史方案");
        t2.getStyleClass().add("card-title");
        historyList.setPrefWidth(260);
        historyList.setItems(historyItems);
        historyList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> showDetail());
        leftCard.getChildren().addAll(t2, historyList);

        HBox root = new HBox(14);
        root.getChildren().addAll(leftCard, centerCard);
        HBox.setHgrow(centerCard, Priority.ALWAYS);
        getChildren().addAll(topCard, root);

        btnGen.setOnAction(e -> generatePlan());
        refreshHistory();
    }

    private void refreshHistory() {
        historyItems.clear();
        historyMap.clear();
        for (String[] row : DBUtil.getAIDietRecordsByUser(DBUtil.currentUsername, 50)) {
            int id = Integer.parseInt(row[0]);
            historyMap.put(id, row);
            historyItems.add(id + " | " + row[4] + " | " + row[1]);
        }
    }

    private void showDetail() {
        String selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int id = Integer.parseInt(selected.split(" \\| ")[0]);
        String[] row = historyMap.get(id);
        if (row != null) {
            taPlan.setText("【你的需求】\n" + row[1] + "\n\n【AI 饮食方案】\n" + row[2]);
        }
    }

    private void generatePlan() {
        String goal = cbGoal.getValue();
        String custom = tfCustom.getText().trim();
        String query = custom.isEmpty() ? goal : custom;
        String localKey = tfApiKey.getText().trim();
        String apiKey = localKey.isEmpty() ? DBUtil.getAIApiConfig().getOrDefault("api_key", "") : localKey;
        String plan;
        if (apiKey.isEmpty()) {
            plan = generateLocalPlan(query);
        } else {
            try {
                plan = DBUtil.callOpenAIChat(apiKey, buildPrompt(query));
            } catch (Exception ex) {
                plan = "AI 调用失败（" + ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? "：" + ex.getMessage() : "") + "），已切换本地推荐：\n\n"
                        + generateLocalPlan(query);
            }
        }
        String full = buildHealthConstraintHeader() + "\n\n" + plan;
        String risk = analyzeAllergyRisk(plan);
        if (!risk.isEmpty()) full = full + "\n\n" + risk;
        taPlan.setText(full);
        DBUtil.saveAIDietRecord(DBUtil.currentUsername, query, full);
        refreshHistory();
    }

    private String buildPrompt(String goal) {
        Map<String, Object> latest = DBUtil.getLatestHealthRecord();
        StringBuilder dataSb = new StringBuilder();
        if (latest != null) {
            dataSb.append(String.format("用户最新数据：体重 %.1f kg, BMI %.1f, 体脂 %.1f%%, TDEE %.0f kcal。",
                    num(latest, "weight"), num(latest, "bmi"), num(latest, "body_fat"), num(latest, "tdee")));
        }
        int[] diet = DBUtil.getTodayDietSummary();
        if (diet[0] > 0) {
            dataSb.append(String.format("今日已摄入 %d kcal, 蛋白质 %.1fg, 碳水 %.1fg, 脂肪 %.1fg。",
                    diet[0], diet[1] / 100.0, diet[2] / 100.0, diet[3] / 100.0));
        }
        String a = DBUtil.currentAllergies == null ? "" : DBUtil.currentAllergies.trim();
        String c = DBUtil.currentChronicDiseases == null ? "" : DBUtil.currentChronicDiseases.trim();
        if (!a.isEmpty() || !c.isEmpty()) {
            dataSb.append("\n用户健康约束：");
            if (!a.isEmpty()) dataSb.append("过敏源=[").append(a).append("]，严禁推荐含这些食材的食物；");
            if (!c.isEmpty()) dataSb.append("慢性病=[").append(c).append("]，请针对这些疾病给出饮食禁忌与宜忌提示；");
        }
        return dataSb + "\n请为用户制定一份「" + goal + "」的一日三餐饮食方案，给出每餐热量、食材和注意事项，并明确标注与用户过敏源/慢性病相关的禁忌，控制在 400 字以内。";
    }

    private String generateLocalPlan(String goal) {
        Map<String, Object> latest = DBUtil.getLatestHealthRecord();
        int[] diet = DBUtil.getTodayDietSummary();
        int intakeCal = diet[0];
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════\n");
        sb.append("       AI 饮食推荐方案 — ").append(goal).append("\n");
        sb.append("═════════════════════════════════\n\n");
        double tdee = 1800, weight = 65, bmi = 22;
        if (latest != null) {
            tdee = num(latest, "tdee");
            weight = num(latest, "weight");
            bmi = num(latest, "bmi");
            sb.append(String.format("基础信息：体重 %.1f kg，BMI %.1f，每日消耗约 %.0f kcal\n\n", weight, bmi, tdee));
        } else {
            sb.append("暂未检测到健康记录，使用默认参考值。\n\n");
        }

        int targetCal = (int) tdee;
        if ("减脂".equals(goal)) targetCal = (int) (tdee * 0.85);
        else if ("增肌".equals(goal)) targetCal = (int) (tdee * 1.1);
        else if ("控糖".equals(goal)) targetCal = (int) tdee;

        sb.append(String.format("目标热量：%d kcal/日", targetCal));
        if (intakeCal > 0) sb.append(String.format("（今日已摄入 %d kcal）", intakeCal));
        sb.append("\n\n");

        sb.append("【一日三餐建议】\n");
        int breakfast = (int) (targetCal * 0.3);
        int lunch = (int) (targetCal * 0.4);
        int dinner = (int) (targetCal * 0.3);
        sb.append(String.format("早餐：约 %d kcal — 全麦面包/燕麦 + 鸡蛋 + 牛奶/豆浆\n", breakfast));
        sb.append(String.format("午餐：约 %d kcal — 米饭(小份) + 瘦肉/鱼 + 大量蔬菜\n", lunch));
        sb.append(String.format("晚餐：约 %d kcal — 杂粮 + 豆腐/鸡胸肉 + 凉拌蔬菜\n\n", dinner));

        sb.append("【注意事项】\n");
        if ("减脂".equals(goal)) {
            sb.append("• 减少精制碳水和含糖饮料\n• 增加蔬菜和优质蛋白\n• 保持每周 3-5 次有氧运动\n");
        } else if ("增肌".equals(goal)) {
            sb.append("• 每公斤体重摄入 1.6-2g 蛋白质\n• 训练后 30 分钟内补充碳水+蛋白\n• 保证充足睡眠\n");
        } else if ("控糖".equals(goal)) {
            sb.append("• 选择低升糖指数(GI)食物\n• 减少白米饭、白面包、甜食\n• 餐餐搭配蔬菜和蛋白质\n");
        } else {
            sb.append("• 保持当前热量平衡\n• 多样化饮食，避免偏食\n• 规律三餐，少油少盐\n");
        }
        sb.append("\n【你的健康约束】\n");
        String la = DBUtil.currentAllergies == null ? "" : DBUtil.currentAllergies.trim();
        String lc = DBUtil.currentChronicDiseases == null ? "" : DBUtil.currentChronicDiseases.trim();
        if (la.isEmpty() && lc.isEmpty()) {
            sb.append("• 你尚未填写过敏源/慢性病，建议在「数据大屏-编辑过敏源/慢性病」补充，以获得规避建议\n");
        } else {
            if (!la.isEmpty()) sb.append("• 过敏源：").append(la).append(" —— 本方案已尽量规避，食用前请再次确认食材不含上述成分\n");
            if (!lc.isEmpty()) sb.append("• 慢性病：").append(lc).append(" —— 请结合疾病饮食原则（如糖尿病控糖、高血压少盐、高血脂少油）选择食材\n");
        }

        sb.append("\n本方案由本地健康规则生成，仅供参考。");
        return sb.toString();
    }

    private String buildHealthConstraintHeader() {
        String a = DBUtil.currentAllergies == null ? "" : DBUtil.currentAllergies.trim();
        String c = DBUtil.currentChronicDiseases == null ? "" : DBUtil.currentChronicDiseases.trim();
        StringBuilder sb = new StringBuilder();
        if (a.isEmpty() && c.isEmpty()) {
            sb.append("健康档案约束：你尚未填写过敏源/慢性病（可在「数据大屏-编辑过敏源/慢性病」补充）");
        } else {
            sb.append("⚠ 健康档案约束");
            if (!a.isEmpty()) sb.append("　过敏源：").append(a);
            if (!c.isEmpty()) sb.append("　慢性病：").append(c);
            sb.append("\n（以下方案已尽量规避你的过敏源；如有疑问请人工核对）");
        }
        return sb.toString();
    }

    private String analyzeAllergyRisk(String plan) {
        String a = DBUtil.currentAllergies == null ? "" : DBUtil.currentAllergies.trim();
        if (a.isEmpty() || plan == null || plan.isEmpty()) return "";
        // 借助过敏源知识库把类别词展开为具体食材关键词（如 海鲜→鱼/虾/蟹），再做文本扫描
        List<String> triggers = AllergenKB.expandTriggers(a);
        LinkedHashSet<String> conflicts = new LinkedHashSet<>();
        for (String kw : triggers) {
            if (kw.isEmpty()) continue;
            int idx = plan.indexOf(kw);
            while (idx >= 0) {
                String pre = idx >= 8 ? plan.substring(idx - 8, idx) : plan.substring(0, idx);
                if (!pre.contains("避免") && !pre.contains("忌") && !pre.contains("禁")
                        && !pre.contains("不要") && !pre.contains("不宜") && !pre.contains("少吃")
                        && !pre.contains("不含") && !pre.contains("勿") && !pre.contains("远离")
                        && !pre.contains("无")) {
                    conflicts.add(kw);
                }
                idx = plan.indexOf(kw, idx + kw.length());
            }
        }
        if (conflicts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("⚠ 风险提示：推荐方案中提到你的过敏相关食材「").append(String.join("、", conflicts)).append("」，");
        sb.append("请务必人工核对，确认未含相关食材后再食用。");
        return sb.toString();
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
