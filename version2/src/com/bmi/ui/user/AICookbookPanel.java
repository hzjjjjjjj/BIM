package com.bmi.ui.user;

import com.bmi.db.DBUtil;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import javafx.concurrent.Task;

import java.util.*;
import java.util.Map;

/** AI 菜谱生成面板 — 自由输入需求, AI 给出菜谱 + 采购清单 */
public class AICookbookPanel extends VBox {
    private final TextArea taRequest = new TextArea();
    private final TextField tfApiKey = new TextField();
    private final TextArea taResult = new TextArea();
    private final ListView<String> historyList = new ListView<>();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final Map<Integer, String[]> historyMap = new HashMap<>();
    private final Button btnGen;

    public AICookbookPanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        taRequest.setPrefHeight(120);
        taRequest.setWrapText(true);
        taRequest.setText("例如：我有鸡蛋、番茄、鸡胸肉，想做清淡口味的晚餐，2个人吃。");
        Label lblHint = new Label("输入你的菜谱需求（食材、口味、人数、餐次等）：");
        lblHint.getStyleClass().add("sub-title");

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);
        Label lk = new Label("API Key (可选):"); lk.getStyleClass().add("sub-title");
        btnGen = new Button("让 AI 生成菜谱");
        btnGen.getStyleClass().add("button-primary");
        bottom.getChildren().addAll(lk, tfApiKey, btnGen);

        VBox topCard = new VBox(10);
        topCard.getStyleClass().add("card");
        Label t0 = new Label("AI 菜谱生成");
        t0.getStyleClass().add("card-title");
        topCard.getChildren().addAll(t0, lblHint, taRequest, bottom);

        taResult.setEditable(false);
        taResult.setWrapText(true);
        taResult.setPrefHeight(360);
        taResult.setStyle("-fx-font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif; -fx-font-size: 13px;");
        taResult.setText("在上方输入你的菜谱需求，例如：\n" +
                "• 我有鸡蛋、番茄、鸡胸肉，想做清淡口味的晚餐，2个人吃。\n" +
                "• 帮我设计一份一周减脂午餐食谱。\n• 用土豆和牛肉做一道家常口味的菜，3个人。\n\n" +
                "输入 API Key 可调用大模型生成更丰富的菜谱；留空则使用本地模板。");
        VBox centerCard = new VBox(10);
        centerCard.getStyleClass().add("card");
        Label t1 = new Label("菜谱与采购清单");
        t1.getStyleClass().add("card-title");
        centerCard.getChildren().addAll(t1, new ScrollPane(taResult){{setFitToWidth(true);}});

        VBox leftCard = new VBox(10);
        leftCard.getStyleClass().add("card");
        Label t2 = new Label("历史菜谱");
        t2.getStyleClass().add("card-title");
        historyList.setPrefWidth(260);
        historyList.setItems(historyItems);
        historyList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        historyList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> showDetail());
        Button btnSelectAll = new Button("全选");
        btnSelectAll.getStyleClass().add("button-ghost");
        btnSelectAll.setOnAction(e -> historyList.getSelectionModel().selectAll());
        Button btnBatchDelete = new Button("批量删除");
        btnBatchDelete.getStyleClass().add("button-accent");
        btnBatchDelete.setOnAction(e -> batchDelete());
        Button btnDelete = new Button("删除选中");
        btnDelete.getStyleClass().add("button-ghost");
        btnDelete.setOnAction(e -> deleteSelected());
        leftCard.getChildren().addAll(t2, historyList, btnSelectAll, btnBatchDelete, btnDelete);

        HBox root = new HBox(14);
        root.getChildren().addAll(leftCard, centerCard);
        HBox.setHgrow(centerCard, Priority.ALWAYS);
        getChildren().addAll(topCard, root);

        btnGen.setOnAction(e -> generateCookbook());
        refreshHistory();
    }

    private void refreshHistory() {
        historyItems.clear();
        historyMap.clear();
        for (String[] row : DBUtil.getAICookbookRecordsByUser(DBUtil.currentUsername, 50)) {
            int id = Integer.parseInt(row[0]);
            historyMap.put(id, row);
            historyItems.add(id + " | " + row[7] + " | " + row[1]);
        }
    }

    private void showDetail() {
        String selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int id = Integer.parseInt(selected.split(" \\| ")[0]);
        String[] row = historyMap.get(id);
        if (row != null) {
            taResult.setText("【你的需求】\n" + row[1] + "\n\n【AI 菜谱与采购清单】\n" + row[5]);
        }
    }

    private void generateCookbook() {
        String request = taRequest.getText().trim();
        if (request.isEmpty()) { alert("请输入你的菜谱需求"); return; }
        String localKey = tfApiKey.getText().trim();
        String apiKey = localKey.isEmpty() ? DBUtil.getAIApiConfig().getOrDefault("api_key", "") : localKey;
        if (apiKey.isEmpty()) {
            String result = generateLocalCookbook(request);
            taResult.setText(result);
            DBUtil.saveAICookbookRecord(DBUtil.currentUsername, request, "自由输入", "-", 0, result);
            refreshHistory();
            return;
        }
        final String key = apiKey;
        btnGen.setDisable(true);
        taResult.setText("AI 正在生成，请稍候…");
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                try {
                    return DBUtil.callOpenAIChat(key,
                            "请根据以下需求生成一份菜谱和采购清单：" + request +
                            " 要求：1)给出菜名；2)列出所需食材及用量；3)给出详细步骤；4)列出采购清单（用户已提供的食材不要重复列出）；5)给出每人份大致热量。控制在600字以内。");
                } catch (Exception ex) {
                    return "AI 调用失败（" + ex.getClass().getSimpleName()
                            + (ex.getMessage() != null ? "：" + ex.getMessage() : "") + "），已切换本地模板：\n\n"
                            + generateLocalCookbook(request);
                }
            }
            @Override
            protected void succeeded() {
                String r = getValue();
                taResult.setText(r);
                DBUtil.saveAICookbookRecord(DBUtil.currentUsername, request, "自由输入", "-", 0, r);
                refreshHistory();
                btnGen.setDisable(false);
            }
            @Override
            protected void failed() {
                taResult.setText(getMessage() != null ? getMessage() : "AI 调用失败");
                btnGen.setDisable(false);
            }
        };
        new Thread(task).start();
    }

    private String generateLocalCookbook(String request) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════\n");
        sb.append("       AI 菜谱生成结果\n");
        sb.append("═════════════════════════════════\n\n");
        sb.append("【你的需求】\n").append(request).append("\n\n");

        sb.append("【推荐菜谱】\n");
        sb.append("菜名：番茄鸡蛋鸡胸肉\n\n");
        sb.append("步骤：\n");
        sb.append("1. 鸡胸肉切丁，加少许料酒、生抽腌制 10 分钟。\n");
        sb.append("2. 番茄切块，鸡蛋打散备用。\n");
        sb.append("3. 热锅少油，倒入蛋液炒熟盛出。\n");
        sb.append("4. 锅中再加少许油，炒鸡胸至变色，加入番茄炒出汁。\n");
        sb.append("5. 倒入炒好的鸡蛋，加盐调味，翻炒均匀出锅。\n\n");

        sb.append("【热量参考】（每人份）\n");
        sb.append("约 300-400 kcal，具体视食材用量而定。\n\n");

        sb.append("【采购清单】\n");
        sb.append("□ 鸡胸肉\n□ 番茄\n□ 鸡蛋\n□ 食用油\n□ 盐/生抽\n□ 主食（米饭/面条/馒头）\n");
        sb.append("\n本菜谱由本地模板生成，仅供参考。输入 API Key 可获得更贴合你需求的菜谱。");
        return sb.toString();
    }

    private void deleteSelected() {
        String selected = historyList.getSelectionModel().getSelectedItem();
        if (selected == null) { alert("请先选择要删除的历史菜谱"); return; }
        int id = Integer.parseInt(selected.split(" \\| ")[0]);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确定删除这条历史菜谱吗？此操作不可恢复。", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("删除确认");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                if (DBUtil.deleteAICookbookRecord(id, DBUtil.currentUsername)) {
                    refreshHistory();
                    taResult.setText("");
                    alert("已删除该历史菜谱");
                } else {
                    alert("删除失败，请重试");
                }
            }
        });
    }

    private void batchDelete() {
        ObservableList<String> selected = historyList.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) { alert("请先选择要删除的历史菜谱（可多选，或点「全选」）"); return; }
        List<Integer> ids = new ArrayList<>();
        for (String item : selected) ids.add(Integer.parseInt(item.split(" \\| ")[0]));
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确定删除选中的 " + ids.size() + " 条历史菜谱吗？此操作不可恢复。", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("批量删除确认");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                int n = DBUtil.deleteAICookbookRecords(DBUtil.currentUsername, ids);
                refreshHistory();
                taResult.setText("");
                alert("已删除 " + n + " 条历史菜谱");
            }
        });
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
