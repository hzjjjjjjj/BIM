import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.Map;
import java.text.SimpleDateFormat;

/**
 * 历史趋势面板（重构 v2）— 三档指标多选对比 + 面积图 + 统计摘要 + 真实日期轴。
 *
 * 依赖新图表框架：Metric（指标元数据）/ MetricRegistry（注册表）/ TrendModel（算法层）。
 * 新增健康属性只需在 MetricRegistry 注册一条，本面板自动支持（选择器 / 图表 / 统计卡 / 明细表列）。
 */
public class HistoryTrendPanel extends VBox {

    private final AreaChart<String, Number> chart;
    private final TableView<Map<String, Object>> table = new TableView<>();
    private final ObservableList<Map<String, Object>> tableData = FXCollections.observableArrayList();
    private final FlowPane statPane = new FlowPane(12, 12);
    private final Map<String, CheckBox> checkMap = new LinkedHashMap<>();
    private final CheckBox cbPredict = new CheckBox();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat sdfShort = new SimpleDateFormat("MM-dd");
    private static final int PREDICT_DAYS = 14;

    public HistoryTrendPanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        // 控制栏
        HBox ctrl = new HBox(10);
        ctrl.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("勾选指标即可叠加对比；图表按真实记录日期绘制。");
        hint.getStyleClass().add("hint");
        cbPredict.setText("统计卡显示未来" + PREDICT_DAYS + "天预测");
        cbPredict.setSelected(true);
        Button btnRefresh = new Button("刷新数据");
        btnRefresh.getStyleClass().add("button-primary");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        ctrl.getChildren().addAll(hint, sp, cbPredict, btnRefresh);

        // 指标选择（三档分组，可滚动）
        ScrollPane spPicker = new ScrollPane(buildMetricPicker());
        spPicker.setFitToWidth(true);
        spPicker.setPrefHeight(196);
        spPicker.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        // 统计摘要
        statPane.setPrefWrapLength(1000);
        ScrollPane spStat = new ScrollPane(statPane);
        spStat.setFitToWidth(true);
        spStat.setPrefHeight(130);
        spStat.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        // 面积图（真实日期 x 轴）
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("记录日期");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("数值");
        chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle("历史趋势 · 多指标对比");
        chart.setPrefHeight(340);
        chart.setMinHeight(240);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        chart.setAnimated(false);

        // 明细表
        buildTable();
        ScrollPane spTable = new ScrollPane(table);
        spTable.setFitToWidth(true);
        spTable.setPrefHeight(240);

        getChildren().addAll(
                card("控制", ctrl),
                card("指标选择（必备 / 计算 / 仪器）", spPicker),
                card("趋势统计摘要", spStat),
                card("历史趋势图", chart),
                card("历史记录明细", spTable)
        );

        btnRefresh.setOnAction(e -> refresh());
        cbPredict.setOnAction(e -> refresh());
        refresh();
    }

    // ---------- 卡片容器 ----------
    private VBox card(String title, Node content) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        box.getChildren().addAll(t, content);
        return box;
    }

    // ---------- 指标选择器（三档分组） ----------
    private VBox buildMetricPicker() {
        VBox root = new VBox(12);
        Metric.Tier[] tiers = {Metric.Tier.REQUIRED, Metric.Tier.COMPUTED, Metric.Tier.INSTRUMENT};
        String[] names = {"必备属性", "计算属性", "仪器测量属性"};
        String[] subs = {"注册/录入的基础数据", "由必备属性推导", "需测量设备采集"};
        for (int gi = 0; gi < tiers.length; gi++) {
            VBox group = new VBox(6);
            Label gt = new Label(names[gi]);
            gt.getStyleClass().add("metric-group-title");
            Label gs = new Label(subs[gi]);
            gs.getStyleClass().add("metric-group-sub");
            FlowPane checks = new FlowPane(12, 6);
            for (Metric m : MetricRegistry.byTier(tiers[gi])) {
                HBox item = new HBox(6);
                item.setAlignment(Pos.CENTER_LEFT);
                item.getChildren().addAll(new Circle(5, m.color), checkBox(m));
                checks.getChildren().add(item);
            }
            group.getChildren().addAll(gt, gs, checks);
            root.getChildren().add(group);
        }
        return root;
    }

    private CheckBox checkBox(Metric m) {
        CheckBox cb = new CheckBox(m.label + (m.unit.isEmpty() ? "" : " (" + m.unit + ")"));
        cb.setSelected(m.key.equals("weight") || m.key.equals("body_fat") || m.key.equals("bmi"));
        cb.selectedProperty().addListener((ob, ov, nv) -> refresh());
        checkMap.put(m.key, cb);
        return cb;
    }

    // ---------- 刷新（核心） ----------
    private void refresh() {
        List<Metric> selected = new ArrayList<>();
        for (Metric m : MetricRegistry.ALL) {
            CheckBox cb = checkMap.get(m.key);
            if (cb != null && cb.isSelected()) selected.add(m);
        }
        List<Map<String, Object>> records = DBUtil.getHealthRecords(60);
        boolean showPredict = cbPredict.isSelected();

        List<TrendModel.SeriesStat> stats = new ArrayList<>();
        if (!records.isEmpty() && !selected.isEmpty()) {
            stats = TrendModel.analyze(records, selected, PREDICT_DAYS);
        }

        // 图表（多系列面积图，真实日期轴）
        chart.getData().clear();
        for (TrendModel.SeriesStat s : stats) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(s.metric.label);
            for (int i = 0; i < s.values.size(); i++) {
                final int idx = i;
                XYChart.Data<String, Number> d = new XYChart.Data<>(
                        sdfShort.format(s.dates.get(i)), s.values.get(i));
                d.setNode(tooltipNode(s.metric.label, s.dates.get(idx), s.values.get(idx), s.metric.unit));
                series.getData().add(d);
            }
            chart.getData().add(series);
        }
        chart.setTitle(selected.isEmpty() ? "历史趋势" : "历史趋势 · " + selected.size() + " 项指标对比");

        // 统计摘要卡
        statPane.getChildren().clear();
        if (records.isEmpty()) {
            statPane.getChildren().add(new Label("暂无健康记录，请先在「数据录入」中添加。"));
        } else if (selected.isEmpty()) {
            statPane.getChildren().add(new Label("请至少勾选一个指标。"));
        } else {
            for (TrendModel.SeriesStat s : stats) statPane.getChildren().add(buildStatTile(s, showPredict));
        }

        // 明细表
        tableData.setAll(records);
    }

    private Node tooltipNode(String metric, Date date, double value, String unit) {
        Label dot = new Label();
        dot.setStyle("-fx-background-color: #FFFFFF, #2D8CA0; -fx-background-insets: 0, 2px; -fx-background-radius: 7px; -fx-min-width: 12; -fx-min-height: 12; -fx-border-color: #2D8CA0; -fx-border-width: 2; -fx-border-radius: 7px;");
        Tooltip tp = new Tooltip(sdfShort.format(date) + "  " + metric + "\n" + f1(value) + (unit.isEmpty() ? "" : unit));
        tp.setStyle("-fx-font-size: 12px;");
        Tooltip.install(dot, tp);
        return dot;
    }

    private VBox buildStatTile(TrendModel.SeriesStat s, boolean showPredict) {
        VBox tile = new VBox(5);
        tile.getStyleClass().add("stat-tile");
        HBox head = new HBox(6);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().addAll(new Circle(5, s.metric.color), label(s.metric.label, "stat-tile-title"));
        Label val = label(f1(s.last) + (s.metric.unit.isEmpty() ? "" : s.metric.unit), "stat-tile-value");

        String arrow = s.delta > 0.001 ? "▲ +" : s.delta < -0.001 ? "▼ " : "— ";
        String deltaStr = arrow + f1(s.delta) + (s.metric.unit.isEmpty() ? "" : s.metric.unit)
                + "  (" + (s.deltaPct >= 0 ? "+" : "") + f1(s.deltaPct) + "%)";
        Label delta = label(deltaStr, "stat-tile-delta");
        delta.getStyleClass().add(s.delta > 0.001 ? "stat-delta-up" : s.delta < -0.001 ? "stat-delta-down" : "stat-delta-flat");

        StringBuilder ts = new StringBuilder("趋势: " + s.direction);
        if (showPredict && !Double.isNaN(s.predict)) {
            ts.append(" · 预测").append(PREDICT_DAYS).append("天: ").append(f1(s.predict))
              .append(s.metric.unit.isEmpty() ? "" : s.metric.unit);
        }
        Label trend = label(ts.toString(), "stat-tile-trend");

        tile.getChildren().addAll(head, val, delta, trend);
        return tile;
    }

    private Label label(String t, String style) {
        Label l = new Label(t);
        if (style != null) l.getStyleClass().add(style);
        return l;
    }

    // ---------- 明细表（按注册表自动生成列） ----------
    private void buildTable() {
        addCol("日期", r -> {
            Object d = r.get("record_date");
            return d == null ? "-" : sdf.format((Date) d);
        });
        for (Metric m : MetricRegistry.ALL) {
            final Metric mm = m;
            addCol(m.label + (m.unit.isEmpty() ? "" : "(" + m.unit + ")"),
                    r -> f1(mm.value(r)) + (mm.unit.isEmpty() ? "" : mm.unit));
        }
        addCol("体质分类", r -> str(r, "body_type"));
        table.setItems(tableData);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void addCol(String name, java.util.function.Function<Map<String, Object>, String> f) {
        TableColumn<Map<String, Object>, String> c = new TableColumn<>(name);
        c.setCellValueFactory(cb -> new ReadOnlyStringWrapper(f.apply(cb.getValue())));
        table.getColumns().add(c);
    }

    private String f1(double v) {
        return String.format("%.1f", v);
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "-" : v.toString();
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
