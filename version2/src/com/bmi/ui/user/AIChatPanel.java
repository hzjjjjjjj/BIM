package com.bmi.ui.user;

import com.bmi.db.DBUtil;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import javafx.concurrent.Task;

import java.util.*;
import java.util.Map;

/** AI 问答面板 — 用户与 AI 对话, 记录保存到 ai_chat_records */
public class AIChatPanel extends VBox {
    private final ListView<String> historyList = new ListView<>();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final Map<Integer, String[]> historyMap = new HashMap<>();
    private final TextArea taAnswer = new TextArea();
    private final TextField tfQuestion = new TextField();
    private final TextField tfApiKey = new TextField();
    private final Button btnSend;

    public AIChatPanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        // 左侧历史
        VBox leftCard = new VBox(10);
        leftCard.getStyleClass().add("card");
        Label t1 = new Label("历史问答");
        t1.getStyleClass().add("card-title");
        historyList.setPrefWidth(280);
        historyList.setItems(historyItems);
        historyList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        historyList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> showHistoryDetail());
        Button btnSelectAll = new Button("全选");
        btnSelectAll.getStyleClass().add("button-ghost");
        btnSelectAll.setOnAction(e -> historyList.getSelectionModel().selectAll());
        Button btnBatchDelete = new Button("批量删除");
        btnBatchDelete.getStyleClass().add("button-accent");
        btnBatchDelete.setOnAction(e -> batchDelete());
        Button btnDelete = new Button("删除选中");
        btnDelete.getStyleClass().add("button-ghost");
        btnDelete.setOnAction(e -> deleteSelected());
        leftCard.getChildren().addAll(t1, historyList, btnSelectAll, btnBatchDelete, btnDelete);

        // 右侧问答
        HBox input = new HBox(10);
        input.setAlignment(Pos.CENTER_LEFT);
        tfQuestion.setPrefWidth(420);
        btnSend = new Button("发送提问");
        btnSend.getStyleClass().add("button-primary");
        input.getChildren().addAll(tfQuestion, btnSend);

        taAnswer.setEditable(false);
        taAnswer.setWrapText(true);
        taAnswer.setPrefHeight(360);
        taAnswer.setStyle("-fx-font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif; -fx-font-size: 13px;");
        taAnswer.setText("在上方输入你的健康问题，例如：\n" +
                "• 我的 BMI 正常吗？\n• 最近体重上升怎么办？\n• 每天应该摄入多少热量？\n\n" +
                "输入 API Key 可调用大模型；留空则使用本地健康规则回答。");

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);
        Label lk = new Label("API Key (可选):"); lk.getStyleClass().add("hint");
        Button btnClear = new Button("清空");
        btnClear.getStyleClass().add("button-ghost");
        bottom.getChildren().addAll(lk, tfApiKey, btnClear);

        VBox rightCard = new VBox(10);
        rightCard.getStyleClass().add("card");
        Label t2 = new Label("AI 健康问答");
        t2.getStyleClass().add("card-title");
        rightCard.getChildren().addAll(t2, input, new ScrollPane(taAnswer){{setFitToWidth(true);}}, bottom);

        HBox root = new HBox(14);
        root.getChildren().addAll(leftCard, rightCard);
        HBox.setHgrow(rightCard, Priority.ALWAYS);
        getChildren().add(root);

        btnSend.setOnAction(e -> askQuestion());
        tfQuestion.setOnAction(e -> askQuestion());
        btnClear.setOnAction(e -> { tfQuestion.clear(); taAnswer.clear(); });
        refreshHistory();
    }

    private void refreshHistory() {
        historyItems.clear();
        historyMap.clear();
        for (String[] row : DBUtil.getAIChatRecordsByUser(DBUtil.currentUsername, 50)) {
            int id = Integer.parseInt(row[0]);
            historyMap.put(id, row);
            historyItems.add(row[0] + " | " + row[4] + " | " + row[1]);
        }
    }

    private void showHistoryDetail() {
        String selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int id = Integer.parseInt(selected.split(" \\| ")[0]);
        String[] row = historyMap.get(id);
        if (row != null) {
            taAnswer.setText("【问题】\n" + row[1] + "\n\n【AI 回答】\n" + row[2]);
        }
    }

    private void askQuestion() {
        if (btnSend.isDisabled()) return;
        String question = tfQuestion.getText().trim();
        if (question.isEmpty()) { alert("请输入问题"); return; }
        String localKey = tfApiKey.getText().trim();
        String apiKey = localKey.isEmpty() ? DBUtil.getAIApiConfig().getOrDefault("api_key", "") : localKey;
        if (apiKey.isEmpty()) {
            String answer = generateLocalAnswer(question);
            DBUtil.saveAIChatRecord(DBUtil.currentUsername, question, answer);
            taAnswer.setText("【问题】\n" + question + "\n\n【AI 回答】\n" + answer);
            tfQuestion.clear();
            refreshHistory();
            return;
        }
        final String key = apiKey;
        btnSend.setDisable(true);
        taAnswer.setText("【问题】\n" + question + "\n\n【AI 回答】\nAI 正在思考，请稍候…");
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                try {
                    return DBUtil.callOpenAIChat(key, buildPrompt(question));
                } catch (Exception ex) {
                    return "AI 调用失败（" + ex.getClass().getSimpleName()
                            + (ex.getMessage() != null ? "：" + ex.getMessage() : "") + "），已切换本地回答：\n\n"
                            + generateLocalAnswer(question);
                }
            }
            @Override
            protected void succeeded() {
                String answer = getValue();
                DBUtil.saveAIChatRecord(DBUtil.currentUsername, question, answer);
                taAnswer.setText("【问题】\n" + question + "\n\n【AI 回答】\n" + answer);
                tfQuestion.clear();
                refreshHistory();
                btnSend.setDisable(false);
            }
            @Override
            protected void failed() {
                taAnswer.setText("【问题】\n" + question + "\n\n【AI 回答】\n" + (getMessage() != null ? getMessage() : "AI 调用失败"));
                btnSend.setDisable(false);
            }
        };
        new Thread(task).start();
    }

    private String buildPrompt(String question) {
        Map<String, Object> latest = DBUtil.getLatestHealthRecord();
        StringBuilder dataSb = new StringBuilder();
        if (latest != null) {
            dataSb.append(String.format("用户最新健康数据：体重 %.1f kg, BMI %.1f, 体脂 %.1f%%, 身体年龄 %d 岁, 类型 %s。",
                    num(latest, "weight"), num(latest, "bmi"), num(latest, "body_fat"),
                    (int) num(latest, "body_age"), str(latest, "body_type")));
        }
        return dataSb + "\n请作为健康顾问回答以下问题，控制在 300 字以内：\n" + question;
    }

    private String generateLocalAnswer(String question) {
        Map<String, Object> latest = DBUtil.getLatestHealthRecord();
        StringBuilder sb = new StringBuilder();
        sb.append("根据你的健康数据，回答如下：\n\n");
        if (latest != null) {
            double weight = num(latest, "weight");
            double bmi = num(latest, "bmi");
            double bodyFat = num(latest, "body_fat");
            sb.append(String.format("你最新一次记录：体重 %.1f kg，BMI %.1f，体脂率 %.1f%%。\n", weight, bmi, bodyFat));
            if (bmi < 18.5) sb.append("BMI 偏低，建议适当增加热量和蛋白质摄入，配合力量训练。\n");
            else if (bmi > 28) sb.append("BMI 已达到肥胖范围，建议控制总热量、减少精制碳水并增加有氧运动。\n");
            else if (bmi > 24) sb.append("BMI 超重，建议保持热量赤字并规律运动。\n");
            else sb.append("BMI 在正常范围，请继续保持。\n");
        } else {
            sb.append("暂无健康记录，建议先在「数据录入」页面打卡。\n");
        }
        sb.append("\n若问题更复杂，可在 API Key 处输入密钥调用大模型。");
        return sb.toString();
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "-" : v.toString();
    }

    private void deleteSelected() {
        String selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) { alert("请先选择要删除的历史问答"); return; }
        int id = Integer.parseInt(selected.split(" \\| ")[0]);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确定删除这条历史问答吗？此操作不可恢复。", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("删除确认");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                if (DBUtil.deleteAIChatRecord(id, DBUtil.currentUsername)) {
                    refreshHistory();
                    taAnswer.setText("");
                    alert("已删除该历史问答");
                } else {
                    alert("删除失败，请重试");
                }
            }
        });
    }

    private void batchDelete() {
        ObservableList<String> selected = historyList.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) { alert("请先选择要删除的历史问答（可多选，或点「全选」）"); return; }
        List<Integer> ids = new ArrayList<>();
        for (String item : selected) ids.add(Integer.parseInt(item.split(" \\| ")[0]));
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确定删除选中的 " + ids.size() + " 条历史问答吗？此操作不可恢复。", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("批量删除确认");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                int n = DBUtil.deleteAIChatRecords(DBUtil.currentUsername, ids);
                refreshHistory();
                taAnswer.setText("");
                alert("已删除 " + n + " 条历史问答");
            }
        });
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
