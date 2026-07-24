package com.bmi.ui.admin;

import com.bmi.db.DBUtil;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;

import java.util.List;
import java.util.Map;

/**
 * 健康文章管理面板（JavaFX 8 重写 version1 ContentManagementPanel 文章部分）
 * 列表、新增/编辑/删除（官方直发）。含「投稿审核队列」：用户/机构投稿待管理员审核后发布。
 * getHealthArticles() 返回 String[]{id,title,category,status,author,author_type,published_at}。
 */
public class ArticleManagePanel extends VBox {
    private final TableView<String[]> table = new TableView<>();
    private final TableView<String[]> pendingTable = new TableView<>();

    public ArticleManagePanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label title = new Label("健康文章");
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        HBox ctrl = new HBox(8);
        ctrl.setAlignment(Pos.CENTER_LEFT);
        Button btnAdd = new Button("新增");
        Button btnEdit = new Button("编辑");
        btnAdd.getStyleClass().add("button-primary");
        btnEdit.getStyleClass().add("button-primary");
        Button btnViewMain = new Button("查看");
        btnViewMain.getStyleClass().add("button-ghost");
        ctrl.getChildren().addAll(btnAdd, btnEdit, btnViewMain);
        card.getChildren().add(ctrl);

        table.getColumns().addAll(
                colA("ID", 0, 60),
                colA("标题", 1, 220),
                colA("分类", 2, 100),
                colA("状态", 3, 80),
                colA("作者", 4, 100),
                colTypeLabel(5, 80),
                colA("发布时间", 6, 150)
        );
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) viewManaged(); });
        card.getChildren().add(table);
        getChildren().add(card);

        // 审核队列：用户/机构投稿，待管理员审核
        VBox reviewCard = new VBox(10);
        reviewCard.getStyleClass().add("card");
        Label rTitle = new Label("投稿审核队列（用户/机构投稿，待审核）");
        rTitle.getStyleClass().add("card-title");
        HBox rCtrl = new HBox(8);
        rCtrl.setAlignment(Pos.CENTER_LEFT);
        Button btnView = new Button("查看内容");
        Button btnApprove = new Button("通过(发布)");
        Button btnReject = new Button("驳回");
        btnView.getStyleClass().add("button-ghost");
        btnApprove.getStyleClass().add("button-primary");
        btnReject.getStyleClass().add("button-accent");
        rCtrl.getChildren().addAll(btnView, btnApprove, btnReject);
        reviewCard.getChildren().addAll(rTitle, rCtrl);
        pendingTable.getColumns().addAll(
                colA("ID", 0, 60),
                colA("标题", 1, 220),
                colA("分类", 2, 100),
                colA("作者", 3, 100),
                colTypeLabel(4, 80),
                colA("提交时间", 5, 150)
        );
        pendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        pendingTable.setOnMouseClicked(e -> { if (e.getClickCount() == 2) viewPending(); });
        reviewCard.getChildren().add(pendingTable);
        getChildren().add(reviewCard);

        btnAdd.setOnAction(e -> editArticle(0));
        btnViewMain.setOnAction(e -> viewManaged());
        btnApprove.setOnAction(e -> reviewSelected(true));
        btnReject.setOnAction(e -> reviewSelected(false));
        btnView.setOnAction(e -> viewPending());
        btnEdit.setOnAction(e -> {
            int id = getSelectedId();
            if (id > 0) editArticle(id);
        });

        loadArticles();
    }

    private TableColumn<String[], String> colA(String name, int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>(name);
        c.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue()[idx]));
        c.setPrefWidth(w);
        return c;
    }

    private void loadArticles() {
        table.getItems().clear();
        for (String[] row : DBUtil.getHealthArticles()) {
            table.getItems().add(row);
        }
        pendingTable.getItems().clear();
        for (String[] row : DBUtil.getPendingArticles()) {
            pendingTable.getItems().add(row);
        }
    }

    /** 审核选中投稿：approve=true 通过发布；false 弹出驳回理由（支持 AI 生成）。 */
    private void reviewSelected(boolean approve) {
        String[] sel = pendingTable.getSelectionModel().getSelectedItem();
        if (sel == null) { warn("请先在审核队列中选择一条投稿"); return; }
        int id = Integer.parseInt(sel[0]);
        if (approve) {
            if (DBUtil.approveArticle(id, DBUtil.currentUsername)) {
                DBUtil.logAction("ADMIN", DBUtil.currentUsername, "通过文章投稿", sel[1]);
                loadArticles();
                alert("已通过并发布：" + sel[1]);
            } else err("操作失败");
        } else {
            rejectWithAI(id, sel);
        }
    }

    /** 驳回投稿：弹窗含「AI 生成驳回理由」按钮，基于文章正文生成可编辑的驳回理由 */
    private void rejectWithAI(int id, String[] sel) {
        TextArea taReason = new TextArea();
        taReason.setWrapText(true);
        taReason.setPrefRowCount(5);
        taReason.setPromptText("可点「AI 生成驳回理由」自动生成，也可手动填写 / 修改");

        Button btnGen = new Button("AI 生成驳回理由");
        btnGen.getStyleClass().add("button-accent");
        Label lbState = new Label("");
        lbState.getStyleClass().add("hint");

        btnGen.setOnAction(e -> {
            Map<String, String> art = DBUtil.getHealthArticleById(id);
            if (art == null || art.isEmpty()) { warn("文章不存在或已被删除"); return; }
            String key = DBUtil.getAIApiConfig().getOrDefault("api_key", "");
            if (key.isEmpty()) {
                warn("管理员尚未配置 AI API Key，无法生成，请手动填写驳回理由。");
                return;
            }
            btnGen.setDisable(true);
            lbState.setText("AI 正在生成驳回理由…");
            String title = art.get("title") == null ? "" : art.get("title");
            String content = art.get("content") == null ? "" : art.get("content");
            String prompt = "你是一位健康科普内容审核专家。请审阅下面这篇投稿文章，给出简明、专业、"
                    + "建设性的「驳回理由」（文章暂不适合发布的原因及修改建议）。要求：1) 控制在150字以内；"
                    + "2) 语气客观专业；3) 只输出驳回理由本身，不要标题或客套话。\n\n标题："
                    + title + "\n正文：" + content;
            Task<String> task = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return DBUtil.callOpenAIChat(key, prompt);
                }
                @Override
                protected void succeeded() {
                    taReason.setText(getValue());
                    lbState.setText("已生成，可继续修改后点「驳回」");
                    btnGen.setDisable(false);
                }
                @Override
                protected void failed() {
                    lbState.setText("AI 生成失败：" + (getMessage() != null ? getMessage() : "")
                            + "，请手动填写");
                    btnGen.setDisable(false);
                }
            };
            new Thread(task).start();
        });

        HBox btnRow = new HBox(8, btnGen, lbState);
        VBox box = new VBox(10, new Label("驳回原因（将记录并通知作者）"), taReason, btnRow);
        box.setPadding(new Insets(8));
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("驳回投稿");
        d.setHeaderText(null);
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResizable(true);
        d.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String reason = taReason.getText().trim();
            if (reason.isEmpty()) { warn("驳回原因不能为空"); return; }
            if (DBUtil.rejectArticle(id, DBUtil.currentUsername, reason)) {
                DBUtil.logAction("ADMIN", DBUtil.currentUsername, "驳回文章投稿", sel[1] + " 原因:" + reason);
                loadArticles();
                alert("已驳回：" + sel[1]);
            } else err("操作失败");
        });
    }

    /** 审核时查看投稿正文，避免盲审 */
    private void viewPending() {
        String[] sel = pendingTable.getSelectionModel().getSelectedItem();
        if (sel == null) { warn("请先在审核队列中选择一条投稿"); return; }
        viewArticleContent(sel);
    }

    /** 主列表双击/查看：阅读已管理文章正文 */
    private void viewManaged() {
        String[] sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { warn("请先选择一行"); return; }
        viewArticleContent(sel);
    }

    /** 按选中行打开正文查看弹窗（只读） */
    private void viewArticleContent(String[] sel) {
        int id = Integer.parseInt(sel[0]);
        Map<String, String> art = DBUtil.getHealthArticleById(id);
        if (art == null || art.isEmpty()) { alert("未找到该文章"); return; }
        Label lblTitle = new Label(art.get("title") == null ? "" : art.get("title"));
        lblTitle.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#1E6478;");
        String srcType = typeLabel(art.get("author_type"));
        String srcName = art.get("author") == null ? "" : art.get("author");
        Label lblMeta = new Label("分类：" + (art.get("category") == null ? "-" : art.get("category"))
                + "  ｜  来源：" + srcType + (srcName.isEmpty() ? "" : "(" + srcName + ")")
                + "  ｜  状态：" + (art.get("status") == null ? "-" : art.get("status")));
        lblMeta.getStyleClass().add("hint");
        TextArea ta = new TextArea(art.get("content") == null ? "" : art.get("content"));
        ta.setWrapText(true); ta.setEditable(false);
        ta.setPrefSize(560, 360);
        ta.setStyle("-fx-font-family:'Microsoft YaHei UI','Microsoft YaHei',sans-serif; -fx-font-size:13px;");
        VBox content = new VBox(10, lblTitle, lblMeta, ta);
        content.setPadding(new Insets(10));
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("查看文章内容");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(content);
        dlg.setResizable(true);
        dlg.showAndWait();
    }

    /** 来源类型中文标签：user=用户, institution=机构, admin/空=官方 */
    private String typeLabel(String t) {
        if ("user".equals(t)) return "用户";
        if ("institution".equals(t)) return "机构";
        if ("admin".equals(t)) return "官方";
        return "官方";
    }

    /** 来源列：将 author_type 映射为中文标签 */
    private TableColumn<String[], String> colTypeLabel(int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>("来源");
        c.setCellValueFactory(data -> new ReadOnlyStringWrapper(typeLabel(data.getValue()[idx])));
        c.setPrefWidth(w);
        return c;
    }

    private int getSelectedId() {
        String[] sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("请先选择一行");
            return -1;
        }
        return Integer.parseInt(sel[0]);
    }

    private void editArticle(int id) {
        String title = "", category = "健康科普", content = "";
        if (id > 0) {
            String[] row = table.getSelectionModel().getSelectedItem();
            title = row[1];
            category = row[2];
        }
        TextField tfTitle = new TextField(title);
        TextField tfCategory = new TextField(category);
        TextArea taContent = new TextArea(content);
        taContent.setPrefRowCount(6);
        taContent.setWrapText(true);
        tfTitle.getStyleClass().add("text-field");
        tfCategory.getStyleClass().add("text-field");
        taContent.getStyleClass().add("text-field");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("标题"), tfTitle);
        g.addRow(1, new Label("分类"), tfCategory);
        g.add(new Label("内容"), 0, 2);
        g.add(taContent, 1, 2);

        if (confirmDialog(id > 0 ? "编辑文章" : "新增文章", g)) {
            if (DBUtil.saveHealthArticle(id, tfTitle.getText().trim(),
                    taContent.getText(), tfCategory.getText().trim(),
                    DBUtil.currentUsername, "admin", "已发布")) {
                DBUtil.logAction("ADMIN", DBUtil.currentUsername, "保存文章", tfTitle.getText());
                loadArticles();
            } else {
                err("保存失败");
            }
        }
    }

    private boolean confirmDialog(String title, GridPane content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
        a.setTitle(title);
        a.getDialogPane().setContent(content);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }

    private void warn(String m) {
        new Alert(Alert.AlertType.WARNING, m, ButtonType.OK).showAndWait();
    }

    private void err(String m) {
        new Alert(Alert.AlertType.ERROR, m, ButtonType.OK).showAndWait();
    }

    private boolean confirm(String title, String m) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, m, ButtonType.YES, ButtonType.NO);
        a.setTitle(title);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }
}
