package com.bmi.ui;

import com.bmi.App;
import com.bmi.db.DBUtil;
import com.bmi.ui.user.AIChatPanel;
import com.bmi.ui.user.AICookbookPanel;
import com.bmi.ui.user.AIDietPanel;
import com.bmi.ui.user.AchievementPanel;
import com.bmi.ui.user.AnalysisPanel;
import com.bmi.ui.user.DataInputPanel;
import com.bmi.ui.user.DietPanel;
import com.bmi.ui.user.GoalPlanPanel;
import com.bmi.ui.user.HealthArticlePanel;
import com.bmi.ui.user.HistoryTrendPanel;
import com.bmi.ui.user.PredictionPanel;
import com.bmi.ui.user.UserDashboardPanel;
import com.bmi.ui.user.WaterPanel;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.application.Platform;

import java.util.Arrays;
import java.util.List;

public class MainView {
    private BorderPane root = new BorderPane();

    public MainView() {
        // 顶栏
        HBox top = new HBox(12);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-background-color: linear-gradient(to right, #1E6478, #2D8CA0 60%, #1E6478); -fx-border-color: transparent transparent #154B5A transparent; -fx-border-width: 0 0 2 0; -fx-effect: dropshadow(gaussian, rgba(33,80,100,0.25), 10, 0.2, 0, 2);");
        Label user = new Label("欢迎, " + DBUtil.currentUsername);
        user.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        Button btnWater = new Button("💧 记录喝水");
        btnWater.getStyleClass().add("button-primary");
        Button btnMsg = new Button("消息中心");
        btnMsg.getStyleClass().add("button-primary");
        Button btnApiKey = new Button("\ud83d\udd11 AI Key");
        btnApiKey.getStyleClass().add("button-ghost");
        btnApiKey.setOnAction(e -> openApiKeyDialog());
        Button btnLogout = new Button("退出登录");
        btnLogout.getStyleClass().add("button-accent");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(user, spacer, btnWater, btnMsg, btnApiKey, btnLogout);
        root.setTop(top);

        btnMsg.setOnAction(e -> NotificationCenter.show(DBUtil.currentUsername, false));
        btnLogout.setOnAction(e -> {
            DBUtil.currentUsername = "";
            App.showLogin();
        });

        // 左侧导航 + 内容区（替代 TabPane，规避 JavaFX 左侧 Tab 文字旋转缺陷）
        // 按业务分组、可折叠：侧栏可见长度由「分组数」决定，与功能总数解耦，新增功能只需归入某组
        List<SideNav.NavSection> sections = Arrays.asList(
                new SideNav.NavSection("健康数据", Arrays.asList(
                        tab("数据大屏", new UserDashboardPanel()),
                        tab("数据录入", new DataInputPanel()),
                        tab("历史趋势", new HistoryTrendPanel()),
                        tab("分析评估", new AnalysisPanel()),
                        tab("预测分析", new PredictionPanel()))),
                new SideNav.NavSection("饮食 · 饮水", Arrays.asList(
                        tab("饮水记录", new WaterPanel()),
                        tab("饮食管理", new DietPanel()))),
                new SideNav.NavSection("目标 · 计划", Arrays.asList(
                        tab("目标计划", new GoalPlanPanel()))),
                new SideNav.NavSection("AI 智能", Arrays.asList(
                        tab("AI 问答", new AIChatPanel()),
                        tab("AI 饮食推荐", new AIDietPanel()),
                        tab("AI 菜谱生成", new AICookbookPanel()))),
                new SideNav.NavSection("我的", Arrays.asList(
                        tab("成就徽章", new AchievementPanel()),
                        tab("健康资讯", new HealthArticlePanel())))
        );
        SideNav nav = new SideNav("健康管理系统", sections);
        root.setLeft(nav.getSidebar());
        root.setCenter(nav.getContent());
        btnWater.setOnAction(e -> nav.selectByTitle("饮水记录"));

        // 进入系统时检查今日饮水：太少则弹窗提醒，并向消息中心写入一条未读「喝水提醒」
        if (!waterReminderShownThisSession && DBUtil.isWaterIntakeLow()) {
            waterReminderShownThisSession = true;
            int total = DBUtil.getTodayWaterTotal();
            int goal = DBUtil.getDailyWaterGoal();
            String content = String.format(
                    "您今日已饮水 %d ml，距离目标 %d ml 还差 %d ml。\n请及时补充水分，少量多次、均匀饮水更利于健康。",
                    total, goal, Math.max(0, goal - total));
            if (!DBUtil.hasWaterReminderToday()) {
                DBUtil.saveNotification("系统", DBUtil.currentUsername, "喝水提醒", content, "健康提醒");
            }
            final String msg = "💧 该喝水了！\n\n" + content;
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
                alert.setTitle("喝水提醒");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
        }
    }

    /** 每次会话仅弹一次提醒，避免重复打扰 */
    private static boolean waterReminderShownThisSession = false;

    private static Tab tab(String name, javafx.scene.Parent content) {
        return new Tab(name, content);
    }

    public BorderPane getRoot() {
        return root;
    }

    /** 用户端统一的 AI Key 设置入口：一次设置，三个 AI 面板（问答/饮食/菜谱）共用 */
    private void openApiKeyDialog() {
        Alert dlg = new Alert(Alert.AlertType.NONE);
        dlg.setTitle("AI Key 设置");
        dlg.setHeaderText("设置你的专属 AI Key");
        Label hint = new Label("在此设置后，所有 AI 功能（AI 问答 / AI 饮食 / AI 菜谱）共用此 Key。\n" +
                "留空则使用管理员预设 AI（无需自己的 key）。Key 仅保存在本机数据库，仅本人可见。");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        TextField tf = new TextField(DBUtil.currentUserApiKey);
        tf.setPromptText("粘贴你的 API Key（可选）");
        tf.setPrefWidth(380);
        VBox box = new VBox(10, hint, tf);
        box.setPadding(new Insets(6));
        dlg.getDialogPane().setContent(box);
        ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        ButtonType clear = new ButtonType("清空并使用预设", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().addAll(save, clear, cancel);
        dlg.showAndWait().ifPresent(bt -> {
            if (bt == clear) {
                DBUtil.setCurrentUserApiKey("");
                alertMsg("已清空，将使用管理员预设 AI");
            } else if (bt == save) {
                DBUtil.setCurrentUserApiKey(tf.getText());
                alertMsg("AI Key 已保存，三个 AI 面板将共用");
            }
        });
    }

    private void alertMsg(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
