package com.bmi.ui.user;

import com.bmi.db.DBUtil;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;

/** 健康资讯面板 — 浏览已发布文章, 双击查看正文 */
public class HealthArticlePanel extends VBox {
    private final TableView<String[]> table = new TableView<>();
    private final ObservableList<String[]> items = FXCollections.observableArrayList();

    public HealthArticlePanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        HBox ctrl = new HBox(10);
        ctrl.setAlignment(Pos.CENTER_LEFT);
        Button btnRefresh = new Button("刷新");
        btnRefresh.getStyleClass().add("button-primary");
        Button btnSubmit = new Button("我要投稿");
        Button btnMine = new Button("我的投稿");
        btnSubmit.getStyleClass().add("button-accent");
        btnMine.getStyleClass().add("button-ghost");
        Label hint = new Label("点击文章行查看正文");
        hint.getStyleClass().add("hint");
        ctrl.getChildren().addAll(btnRefresh, btnSubmit, btnMine, hint);

        VBox ctrlCard = new VBox(10);
        ctrlCard.getStyleClass().add("card");
        Label t1 = new Label("健康资讯");
        t1.getStyleClass().add("card-title");
        ctrlCard.getChildren().addAll(t1, ctrl);

        table.getColumns().addAll(
                colA("ID", 0, 60),
                colA("标题", 1, 240),
                colA("分类", 2, 110),
                colA("作者", 3, 110),
                colTypeLabel(4, 80),
                colA("发布时间", 5, 150)
        );
        table.setItems(items);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                viewArticle(table.getSelectionModel().getSelectedItem());
            }
        });

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label t2 = new Label("已发布文章");
        t2.getStyleClass().add("card-title");
        card.getChildren().add(new ScrollPane(table){{setFitToWidth(true);}});

        getChildren().addAll(ctrlCard, card);
        btnRefresh.setOnAction(e -> loadArticles());
        btnSubmit.setOnAction(e -> submitArticle());
        btnMine.setOnAction(e -> showMySubmissions());
        loadArticles();
    }

    private void loadArticles() {
        items.setAll(DBUtil.getPublishedHealthArticles());
    }

    private void viewArticle(String[] row) {
        int id = Integer.parseInt(row[0]);
        Map<String, String> art = DBUtil.getHealthArticleById(id);
        if (art == null || art.isEmpty()) { alert("未找到该文章"); return; }

        Label lblTitle = new Label(art.get("title") == null ? "" : art.get("title"));
        lblTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1E6478;");
        String srcType = typeLabel(art.get("author_type"));
        String srcName = art.get("author") == null ? "" : art.get("author");
        Label lblMeta = new Label("分类：" + (art.get("category") == null ? "-" : art.get("category"))
                + "  ｜  来源：" + srcType + (srcName.isEmpty() ? "" : "(" + srcName + ")")
                + "  ｜  发布：" + (art.get("published_at") == null ? "-" : art.get("published_at")));
        lblMeta.getStyleClass().add("hint");

        TextArea ta = new TextArea(art.get("content") == null ? "" : art.get("content"));
        ta.setWrapText(true);
        ta.setEditable(false);
        ta.setPrefSize(560, 360);
        ta.setStyle("-fx-font-family: 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif; -fx-font-size: 13px;");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(lblTitle, lblMeta, ta);

        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("健康资讯");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(content);
        dlg.setResizable(true);
        dlg.showAndWait();
    }

    /** 我要投稿：保存为 status=待审核, author_type=user */
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
        if (confirmDialog("我要投稿", g)) {
            String t = tfTitle.getText().trim();
            if (t.isEmpty()) { alert("标题不能为空"); return; }
            if (DBUtil.saveHealthArticle(0, t, ta.getText(), tfCategory.getText().trim(),
                    DBUtil.currentUsername, "user", "待审核")) {
                alert("投稿已提交，等待管理员审核后发布");
            } else alert("提交失败");
        }
    }

    /** 我的投稿：查看本人投稿及状态（已驳回可查看驳回理由） */
    private void showMySubmissions() {
        List<String[]> rows = DBUtil.getMyArticles(DBUtil.currentUsername, "user");
        TableView<String[]> t = new TableView<>();
        t.getColumns().addAll(
                colA("ID", 0, 60), colA("标题", 1, 240), colA("分类", 2, 110),
                colA("状态", 3, 90), colA("提交时间", 4, 150));
        t.setItems(FXCollections.observableArrayList(rows));
        t.setRowFactory(tv -> {
            TableRow<String[]> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (!row.isEmpty() && ev.getClickCount() == 2) {
                    viewMyArticle(Integer.parseInt(row.getItem()[0]));
                }
            });
            return row;
        });
        Button btnView = new Button("查看详情");
        btnView.getStyleClass().add("button-primary");
        btnView.setDisable(true);
        t.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> btnView.setDisable(n == null));
        btnView.setOnAction(e -> {
            String[] sel = t.getSelectionModel().getSelectedItem();
            if (sel != null) viewMyArticle(Integer.parseInt(sel[0]));
        });
        VBox box = new VBox(10, new Label("我的投稿（状态：待审核/已发布/已驳回，双击行或选中后点「查看详情」）"), t, btnView);
        box.setPadding(new Insets(8));
        Alert d = new Alert(Alert.AlertType.INFORMATION);
        d.setTitle("我的投稿");
        d.setHeaderText(null);
        d.getDialogPane().setContent(box);
        d.setResizable(true);
        d.showAndWait();
    }

    /** 查看本人投稿详情：已驳回时展示驳回理由 */
    private void viewMyArticle(int id) {
        Map<String, String> art = DBUtil.getHealthArticleById(id);
        if (art == null || art.isEmpty()) { alert("文章不存在或已被删除"); return; }
        String status = art.get("status") == null ? "" : art.get("status");
        Label lblTitle = new Label(art.get("title") == null ? "" : art.get("title"));
        lblTitle.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#1E6478;");
        Label lblStatus = new Label("状态：" + status);
        lblStatus.getStyleClass().add("hint");
        TextArea ta = new TextArea(art.get("content") == null ? "" : art.get("content"));
        ta.setWrapText(true); ta.setEditable(false); ta.setPrefSize(560, 320);
        ta.setStyle("-fx-font-family:'Microsoft YaHei UI','Microsoft YaHei',sans-serif; -fx-font-size:13px;");
        VBox content = new VBox(10, lblTitle, lblStatus, ta);
        if ("已驳回".equals(status)) {
            String reason = art.get("reject_reason");
            Label lbReason = new Label("驳回理由：" + (reason == null || reason.isEmpty() ? "（未填写）" : reason));
            lbReason.setStyle("-fx-text-fill:#C0392B; -fx-font-weight:bold;");
            lbReason.setWrapText(true);
            content.getChildren().add(lbReason);
        }
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("我的投稿详情");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(content);
        dlg.setResizable(true);
        dlg.showAndWait();
    }

    private boolean confirmDialog(String title, GridPane content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
        a.setTitle(title);
        a.getDialogPane().setContent(content);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private String typeLabel(String t) {
        if ("user".equals(t)) return "用户";
        if ("institution".equals(t)) return "机构";
        if ("admin".equals(t)) return "官方";
        return "官方";
    }

    private TableColumn<String[], String> colA(String name, int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>(name);
        c.setCellValueFactory(cb -> new ReadOnlyStringWrapper(cb.getValue()[idx]));
        c.setPrefWidth(w);
        return c;
    }

    private TableColumn<String[], String> colTypeLabel(int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>("来源");
        c.setCellValueFactory(data -> new ReadOnlyStringWrapper(typeLabel(data.getValue()[idx])));
        c.setPrefWidth(w);
        return c;
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
