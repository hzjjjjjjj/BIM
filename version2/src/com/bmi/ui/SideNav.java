package com.bmi.ui;

import javafx.geometry.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 左侧导航栏（自建，替代 JavaFX TabPane）。
 *
 * 为什么不用 TabPane + setSide(Side.LEFT)：
 *   JavaFX 8 在 Side.LEFT 时会把整个 Tab 头区域旋转 90°，
 *   tab-label 在屏幕上变成竖排，且窄边栏下 4~5 个汉字会被换行/截断。
 *   用普通 Button 列表实现侧边导航，文字天然横向渲染，无旋转缺陷。
 *
 * 为什么用「分组 + 可折叠」：
 *   功能一多，扁平长列表必然要滚动、按钮越压越小也不可持续。
 *   按业务分组，每组可展开/收起，侧栏可见长度被「分组数」锁死，
 *   与功能总数解耦——以后加再多功能也只是归到某个分组里，无需滚动一长条。
 *
 * 内容区使用 ScrollPane：面板内容高于可视区时自动出现滚动条。
 */
public class SideNav {

    /** 导航分组：标题 + 该组包含的页签 */
    public record NavSection(String title, List<Tab> tabs) {}

    private final VBox sidebar = new VBox();
    private final ScrollPane content = new ScrollPane();
    private Button activeBtn;
    private final List<Tab> allTabs = new ArrayList<>();
    private final Map<String, Button> buttonMap = new HashMap<>();   // 页签名 -> 按钮
    private final Map<Button, VBox> btnSection = new HashMap<>();     // 按钮 -> 其所在分组的内容容器
    private final Map<VBox, VBox> sectionBox = new HashMap<>();       // 内容容器 -> 外层分组 box
    private final Map<VBox, Label> sectionChevron = new HashMap<>();  // 内容容器 -> 折叠箭头

    // 导航图标映射（用户端 / 管理端通用，几何字形兼容性最佳）
    private static final Map<String, String> ICON = new HashMap<>();
    static {
        ICON.put("数据大屏", "◉");
        ICON.put("数据录入", "✎");
        ICON.put("历史趋势", "↗");
        ICON.put("分析评估", "◎");
        ICON.put("预测分析", "❖");
        ICON.put("目标计划", "✦");
        ICON.put("饮水记录", "☵");
        ICON.put("饮食管理", "▦");
        ICON.put("AI 问答", "❝");
        ICON.put("AI 饮食推荐", "✚");
        ICON.put("AI 菜谱生成", "◈");
        ICON.put("成就徽章", "★");
        ICON.put("健康资讯", "❏");
        ICON.put("用户管理", "◫");
        ICON.put("食物数据库", "▤");
        ICON.put("运动库", "◆");
        ICON.put("健康文章", "❏");
        ICON.put("AI问答记录", "❝");
        ICON.put("AI饮食记录", "✚");
        ICON.put("AI菜谱记录", "◈");
        ICON.put("Prompt模板", "✎");
        ICON.put("API配置", "◉");
        ICON.put("AI使用统计", "▥");
        ICON.put("数据监控", "◎");
    }

    public SideNav(String brandTitle, List<NavSection> sections) {
        // 内容区：可滚动，宽度贴合视口，高度跟随内容（超出滚动）
        content.setFitToWidth(true);
        content.setFitToHeight(false);
        content.setPannable(false); // 关闭拖拽平移，恢复标准鼠标滚轮滚动
        content.setPadding(new Insets(10));
        content.setStyle("-fx-border-width: 0;");
        content.getStyleClass().add("content-scroll");
        // 标准化鼠标滚轮滚动：每次固定步长，避免灵敏度随硬件 delta 跳变
        content.addEventFilter(ScrollEvent.SCROLL, e -> normalizeScroll(content, e));

        // 全屏/最大化自适应：视口高度变化时，让「短于视口」的面板拉伸撑满，
        // 「高于视口」的面板保持原高（内部滚动条生效），避免全屏后内容缩在左上角、下方留大片空白。
        // 用 fitToHeight 开关而非改 prefHeight：ScrollPane 只有 fitToHeight=true 才会把内容拉伸到视口高。
        content.viewportBoundsProperty().addListener((obs, o, n) -> fillHeight());
        content.heightProperty().addListener((obs, o, n) -> fillHeight());
        // 构造后首帧布局完成再算一次，确保初次进入（如登录后立即进主页）也能撑满
        Platform.runLater(this::fillHeight);

        sidebar.getStyleClass().add("sidebar");

        Label brand = new Label(brandTitle);
        brand.getStyleClass().add("sidebar-brand");

        VBox groups = new VBox(2);
        groups.getStyleClass().add("sidebar-groups");

        ScrollPane scroll = new ScrollPane(groups);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // 侧栏同样标准化滚轮，滚多少是多少，避免灵敏度乱跳（原“滑动很麻烦”的根因）
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> normalizeScroll(scroll, e));

        boolean first = true;
        for (NavSection sec : sections) {
            VBox secContent = buildSection(sec, !first); // 第一组默认展开，其余收起
            groups.getChildren().add(secContent);
            allTabs.addAll(sec.tabs());
            first = false;
        }

        sidebar.getChildren().addAll(brand, scroll);

        if (!allTabs.isEmpty()) {
            Tab firstTab = allTabs.get(0);
            select(buttonMap.get(firstTab.getText()), firstTab);
        }
    }

    /** 构建单个分组（标题栏可点击折叠 + 内部按钮容器） */
    private VBox buildSection(NavSection sec, boolean collapsed) {
        VBox box = new VBox(2);

        Label chevron = new Label(collapsed ? "▸" : "▾");
        chevron.getStyleClass().add("sidebar-chevron");
        Label title = new Label(sec.title());
        title.getStyleClass().add("sidebar-group-title");
        HBox header = new HBox(6, chevron, title);
        header.getStyleClass().add("sidebar-group-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setCursor(javafx.scene.Cursor.HAND);

        VBox items = new VBox(4);
        items.getStyleClass().add("sidebar-nav");
        for (Tab tab : sec.tabs()) {
            String t = tab.getText();
            Label icon = new Label(ICON.getOrDefault(t, "●"));
            icon.getStyleClass().add("sidebar-icon");
            Label txt = new Label(t);
            HBox hb = new HBox(10, icon, txt);
            hb.setAlignment(Pos.CENTER_LEFT);
            Button btn = new Button();
            btn.setGraphic(hb);
            btn.getStyleClass().add("sidebar-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setOnAction(e -> select(btn, tab));
            items.getChildren().add(btn);
            buttonMap.put(t, btn);
            btnSection.put(btn, items);
        }

        // 折叠状态：把 chevron 与内容可见性绑在一起
        header.setOnMouseClicked(e -> setExpanded(box, chevron, items, !items.isVisible()));
        box.getChildren().addAll(header, items);
        sectionBox.put(items, box);
        sectionChevron.put(items, chevron);
        setExpanded(box, chevron, items, !collapsed);
        return box;
    }

    private void setExpanded(VBox box, Label chevron, VBox items, boolean expanded) {
        chevron.setText(expanded ? "▾" : "▸");
        items.setVisible(expanded);
        items.setManaged(expanded);
    }

    private void select(Button btn, Tab tab) {
        if (activeBtn != null) {
            activeBtn.getStyleClass().remove("sidebar-btn-active");
        }
        btn.getStyleClass().add("sidebar-btn-active");
        activeBtn = btn;

        // 选中某页时确保其所在分组已展开，使高亮按钮可见
        VBox sec = btnSection.get(btn);
        if (sec != null && !sec.isVisible()) {
            VBox box = sectionBox.get(sec);
            Label ch = sectionChevron.get(sec);
            if (box != null && ch != null) setExpanded(box, ch, sec, true);
        }

        Node panel = tab.getContent();
        content.setContent(panel);
        fillHeight();
    }

    /** 让当前内容面板在视口内撑满：短则开启 fitToHeight 拉伸到视口高，长则关闭 fitToHeight 保持原高（可滚动）。 */
    private void fillHeight() {
        Node c = content.getContent();
        if (!(c instanceof Region)) return;
        double vh = content.getViewportBounds().getHeight();
        if (vh <= 0) return;
        Region r = (Region) c;
        double natural = r.prefHeight(-1);
        // 清掉历史 pref/max 覆盖，交给 fitToHeight 或自然布局接管
        r.setPrefHeight(Region.USE_COMPUTED_SIZE);
        r.setMaxHeight(Region.USE_COMPUTED_SIZE);
        content.setFitToHeight(natural <= vh);
    }

    private static void normalizeScroll(ScrollPane sp, ScrollEvent e) {
        if (e.getDeltaY() != 0) {
            // 事件落在嵌套的可滚动 ScrollPane（如徽章墙）内：交给它自己处理，
            // 避免主内容区抢占滚动，导致「在徽章墙上滚动却动了整个页面」
            if (insideNestedScrollPane(sp, e.getTarget())) return;
            double viewportH = sp.getViewportBounds().getHeight();
            Node c = sp.getContent();
            double contentH = (c != null) ? c.getBoundsInLocal().getHeight() : 0;
            double scrollable = contentH - viewportH;
            if (scrollable > 0 && viewportH > 0) {
                double sign = Math.signum(e.getDeltaY());
                double step = 50.0; // 每次滚轮滚动 50px，舒适一致
                double frac = sign * step / scrollable;
                double newV = sp.getVvalue() - frac;
                sp.setVvalue(Math.max(sp.getVmin(), Math.min(sp.getVmax(), newV)));
                e.consume();
            }
        }
    }

    /** 判断滚轮事件目标是否位于 sp 之内的某个「可滚动」嵌套 ScrollPane 中 */
    private static boolean insideNestedScrollPane(ScrollPane outer, Object target) {
        if (!(target instanceof Node)) return false;
        Node n = (Node) target;
        while (n != null && n != outer) {
            if (n instanceof ScrollPane) {
                ScrollPane sp = (ScrollPane) n;
                Node c = sp.getContent();
                if (c != null) {
                    double range = c.getBoundsInLocal().getHeight() - sp.getViewportBounds().getHeight();
                    if (range > 1) return true;
                }
            }
            n = n.getParent();
        }
        return false;
    }

    public VBox getSidebar() {
        return sidebar;
    }

    public ScrollPane getContent() {
        return content;
    }

    /** 按标题跳转到指定页（供顶栏快捷按钮使用，如「💧 记录喝水」） */
    public void selectByTitle(String title) {
        Button btn = buttonMap.get(title);
        if (btn != null) {
            for (Tab tab : allTabs) {
                if (title.equals(tab.getText())) {
                    select(btn, tab);
                    return;
                }
            }
        }
    }
}
