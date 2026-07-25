package com.bmi.ui.user;

import com.bmi.db.DBUtil;
import com.bmi.util.BodyModel3D;
import com.bmi.util.BodyModel3D.BodyParams;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import com.bmi.util.BodyModel2D;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 3D 体型渲染与往期对比（个人用户端）。
 * - 根据健康数据用基础几何体拼出拟人 3D 模型（见 BodyModel3D）。
 * - 支持「单期」查看与「对比」：并排两期模型 + 形变动画滑块 + 差值面板，直观看到变化。
 * - 全程异常处理：无数据 / 仅一条记录 / 指标缺失 均有降级与友好提示，绝不崩溃。
 */
public class BodyShape3DPanel extends VBox {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

    /** 一条记录的快照（含原始 Map，便于传给 BodyModel3D.fromRecord） */
    private static class Snap {
        final String label;
        final Map<String, Object> rec;
        Snap(String label, Map<String, Object> rec) { this.label = label; this.rec = rec; }
        double num(String k) { Object o = rec.get(k); return (o instanceof Number) ? ((Number) o).doubleValue() : 0.0; }
    }

    private final List<Snap> snaps = new ArrayList<>();

    // 3D 场景
    private final Group world = new Group();
    private final Group spin = new Group();           // 承载模型，可整体旋转
    private final Rotate ry = new Rotate(0, 0, 0.9, 0, Rotate.Y_AXIS);
    private final Rotate rx = new Rotate(0, 0, 0.9, 0, Rotate.X_AXIS);
    private final PerspectiveCamera cam = new PerspectiveCamera();
    private SubScene sub;
    private final StackPane viewStack = new StackPane();
    private final Label overlayA = new Label();
    private final Label overlayB = new Label();

    // 控件
    private final ComboBox<String> cbA = new ComboBox<>();
    private final ComboBox<String> cbB = new ComboBox<>();
    private final ToggleButton tbSingle = new ToggleButton("单期");
    private final ToggleButton tbCompare = new ToggleButton("对比");
    private final ToggleButton tbSide = new ToggleButton("并排对比");
    private final ToggleButton tbMorph = new ToggleButton("形变动画");
    private final CheckBox chkRotate = new CheckBox("自动旋转");
    private final Slider morphSlider = new Slider(0, 1, 1);
    private final Button btnPlay = new Button("▶ 播放变化");
    private final VBox deltaPanel = new VBox(8);
    private final Label placeholder = new Label("暂无数据，请先录入健康记录");

    // 2D 降级渲染（当运行环境不支持 JavaFX 3D 时使用，任何环境都能显示）
    private final boolean support3D = Platform.isSupported(ConditionalFeature.SCENE3D);
    private final Canvas canvas2D = new Canvas(640, 480);
    private final Label fallbackBanner = new Label(
            "提示：当前环境不支持 3D 硬件加速，已自动切换为 2D 体型示意图（对比 / 变化功能完全一致）");
    // 2D 模式下的期A/期B 体型参数缓存，供滑块拖动 / 窗口缩放时重绘
    private BodyParams lastA, lastB;

    // 动画
    private final Timeline autoRot = new Timeline(new KeyFrame(Duration.millis(50), e -> ry.setAngle(ry.getAngle() + 1.2)));
    private final Timeline morphAnim = new Timeline(
            new KeyFrame(Duration.ZERO, e -> morphSlider.setValue(0)),
            new KeyFrame(Duration.seconds(4), e -> morphSlider.setValue(1)));
    private boolean morphPlaying = false;

    // 拖拽旋转
    private double lastX = 0, lastY = 0;

    public BodyShape3DPanel() {
        setSpacing(12);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: transparent;");

        buildScene();
        buildControls();
        if (!support3D) {
            fallbackBanner.setWrapText(true);
            fallbackBanner.setMaxWidth(Double.MAX_VALUE);
            fallbackBanner.setStyle("-fx-background-color:#1E3A45; -fx-text-fill:#B2EBF2; -fx-padding:8 12; -fx-background-radius:8; -fx-font-size:13px;");
            getChildren().add(0, fallbackBanner);
            chkRotate.setDisable(true);   // 2D 静态剪影无需自动旋转
        }
        loadData();
        rebuild();
    }

    // ==================== 3D 场景搭建（仅一次） ====================
    private void buildScene() {
        world.getChildren().add(new AmbientLight(Color.LIGHTGRAY));
        PointLight pl = new PointLight(Color.WHITE);
        pl.setTranslateX(2.5); pl.setTranslateY(3.5); pl.setTranslateZ(5);
        world.getChildren().add(pl);
        spin.getTransforms().addAll(rx, ry);
        world.getChildren().add(spin);

        cam.setTranslateY(0.95);
        cam.setTranslateZ(5.0);
        sub = new SubScene(world, 600, 460, true, SceneAntialiasing.BALANCED);
        sub.setCamera(cam);
        sub.setFill(Color.web("#0E2A33"));

        viewStack.getChildren().add(sub);
        viewStack.setMinSize(420, 360);
        viewStack.setPrefSize(640, 480);
        // 叠加的期A/期B标签（仅并排模式显示）
        overlayA.setStyle("-fx-background-color: rgba(0,0,0,0.45); -fx-text-fill:white; -fx-padding:4 8; -fx-background-radius:6;");
        overlayB.setStyle("-fx-background-color: rgba(0,0,0,0.45); -fx-text-fill:white; -fx-padding:4 8; -fx-background-radius:6;");
        overlayA.setVisible(false); overlayB.setVisible(false);
        StackPane.setAlignment(overlayA, Pos.TOP_LEFT);
        StackPane.setAlignment(overlayB, Pos.TOP_RIGHT);
        viewStack.getChildren().addAll(overlayA, overlayB);
        // 无数据占位提示（置于最上层，按需显隐）
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setVisible(false);
        placeholder.setManaged(false);
        viewStack.getChildren().add(placeholder);

        // 拖拽旋转
        viewStack.setOnMousePressed(e -> { lastX = e.getX(); lastY = e.getY(); });
        viewStack.setOnMouseDragged(e -> {
            double dx = e.getX() - lastX, dy = e.getY() - lastY;
            lastX = e.getX(); lastY = e.getY();
            ry.setAngle(ry.getAngle() + dx * 0.5);
            rx.setAngle(Math.max(-80, Math.min(80, rx.getAngle() - dy * 0.5)));
        });

        // 自适应大小
        sub.widthProperty().bind(viewStack.widthProperty());
        sub.heightProperty().bind(viewStack.heightProperty());

        // 2D 降级画布（默认隐藏，仅在 support3D=false 时显示）
        canvas2D.setVisible(false);
        canvas2D.setManaged(false);
        viewStack.getChildren().add(canvas2D);
        // 2D 模式下，窗口尺寸变化时需要按新尺寸重绘（3D 由 SubScene 自适应，无需处理）
        viewStack.widthProperty().addListener((ob, o, n) -> { if (!support3D) rebuild(); });
        viewStack.heightProperty().addListener((ob, o, n) -> { if (!support3D) rebuild(); });
    }

    // ==================== 控件 ====================
    private void buildControls() {
        tbSingle.setSelected(true);
        tbSingle.getStyleClass().add("toggle-on");
        tbCompare.getStyleClass().add("toggle-off");
        ToggleGroup mode = new ToggleGroup();
        tbSingle.setToggleGroup(mode); tbCompare.setToggleGroup(mode);
        tbSingle.setOnAction(e -> { syncModeStyle(); rebuild(); });
        tbCompare.setOnAction(e -> { syncModeStyle(); rebuild(); });

        ToggleGroup cmp = new ToggleGroup();
        tbSide.setToggleGroup(cmp); tbMorph.setToggleGroup(cmp);
        tbSide.setSelected(true);
        tbSide.getStyleClass().add("toggle-on");
        tbMorph.getStyleClass().add("toggle-off");
        tbSide.setOnAction(e -> { syncCmpStyle(); rebuild(); });
        tbMorph.setOnAction(e -> { syncCmpStyle(); rebuild(); });

        chkRotate.setOnAction(e -> {
            if (chkRotate.isSelected()) autoRot.play(); else autoRot.stop();
        });

        morphSlider.setPrefWidth(260);
        morphSlider.setShowTickLabels(false);
        morphSlider.setDisable(true);
        morphSlider.valueProperty().addListener((ob, o, n) -> {
            if (tbCompare.isSelected() && tbMorph.isSelected()) rebuild();
        });
        btnPlay.setOnAction(e -> toggleMorphPlay());

        Button btnRefresh = new Button("刷新");
        btnRefresh.getStyleClass().add("button-ghost");
        btnRefresh.setOnAction(e -> { loadData(); rebuild(); });

        cbA.setPrefWidth(180); cbB.setPrefWidth(180);
        cbA.setOnAction(e -> rebuild());
        cbB.setOnAction(e -> rebuild());

        HBox modeBar = new HBox(8, new Label("模式:"), tbSingle, tbCompare,
                new Label("对比方式:"), tbSide, tbMorph, chkRotate, btnRefresh);
        modeBar.setAlignment(Pos.CENTER_LEFT);
        HBox pickBar = new HBox(10, new Label("期A(旧):"), cbA, new Label("期B(新):"), cbB);
        pickBar.setAlignment(Pos.CENTER_LEFT);

        HBox top = new HBox(10, modeBar, pickBar);
        top.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(top);

        // 中部：3D 视图 + 差值面板
        BorderPane center = new BorderPane();
        center.setCenter(viewStack);
        center.setRight(deltaPanel);
        BorderPane.setMargin(deltaPanel, new Insets(0, 0, 0, 12));
        getChildren().add(center);

        // 形变滑块条（仅形变动画模式显示）
        HBox morphBar = new HBox(10, new Label("旧"), morphSlider, new Label("新"), btnPlay);
        morphBar.setAlignment(Pos.CENTER_LEFT);
        morphBar.setVisible(false);
        morphBar.setManaged(false);
        morphBar.setPadding(new Insets(6, 0, 0, 0));
        getChildren().add(morphBar);
        this.morphBar = morphBar;

        placeholder.setStyle("-fx-text-fill:#90A4AE; -fx-font-size:14px;");
        placeholder.setVisible(false);
        placeholder.setAlignment(Pos.CENTER);
    }

    private HBox morphBar;

    private void syncModeStyle() {
        tbSingle.getStyleClass().removeAll("toggle-on", "toggle-off");
        tbCompare.getStyleClass().removeAll("toggle-on", "toggle-off");
        tbSingle.getStyleClass().add(tbSingle.isSelected() ? "toggle-on" : "toggle-off");
        tbCompare.getStyleClass().add(tbCompare.isSelected() ? "toggle-on" : "toggle-off");
    }
    private void syncCmpStyle() {
        tbSide.getStyleClass().removeAll("toggle-on", "toggle-off");
        tbMorph.getStyleClass().removeAll("toggle-on", "toggle-off");
        tbSide.getStyleClass().add(tbSide.isSelected() ? "toggle-on" : "toggle-off");
        tbMorph.getStyleClass().add(tbMorph.isSelected() ? "toggle-on" : "toggle-off");
    }

    private void toggleMorphPlay() {
        morphPlaying = !morphPlaying;
        if (morphPlaying) {
            morphAnim.setAutoReverse(true);
            morphAnim.setCycleCount(Timeline.INDEFINITE);
            morphAnim.play();
            btnPlay.setText("⏸ 暂停");
        } else {
            morphAnim.stop();
            btnPlay.setText("▶ 播放变化");
        }
    }

    // ==================== 数据加载（带异常处理） ====================
    private void loadData() {
        snaps.clear();
        cbA.getItems().clear();
        cbB.getItems().clear();
        try {
            List<Map<String, Object>> recs = DBUtil.getHealthRecords(DBUtil.currentUsername, 200);
            if (recs == null || recs.isEmpty()) {
                showPlaceholder(true, "暂无数据，请先录入健康记录");
                return;
            }
            for (Map<String, Object> r : recs) {
                Object d = r.get("record_date");
                String ds = (d instanceof Date) ? SDF.format((Date) d) : "记录";
                double w = ((Number) r.getOrDefault("weight", 0)).doubleValue();
                Snap s = new Snap(ds + "  (体重 " + String.format("%.1f", w) + "kg)", r);
                snaps.add(s);
                cbA.getItems().add(s.label);
                cbB.getItems().add(s.label);
            }
            if (snaps.size() == 1) {
                showPlaceholder(true, "仅有一条记录，暂无法对比；可录入更多记录后再做往期对比。");
            } else {
                showPlaceholder(false, "");
            }
            cbA.setValue(snaps.get(0).label);                    // 最早
            cbB.setValue(snaps.get(snaps.size() - 1).label);     // 最新
        } catch (Exception ex) {
            DBUtil.logError("BodyShape3DPanel.loadData", ex);
            showPlaceholder(true, "加载健康数据失败: " + ex.getMessage());
        }
    }

    private void showPlaceholder(boolean show, String text) {
        placeholder.setText(text);
        placeholder.setVisible(show);
        placeholder.setManaged(show);
        sub.setVisible(!show);          // 无数据时隐藏 3D 视图，露出提示
        canvas2D.setVisible(!show && !support3D);  // 2D 模式下用画布代替 3D 视图
        canvas2D.setManaged(!show && !support3D);
        overlayA.setVisible(false);
        overlayB.setVisible(false);
    }

    // ==================== 重建 3D 内容 ====================
    private void rebuild() {
        spin.getChildren().clear();
        overlayA.setVisible(false); overlayB.setVisible(false);
        deltaPanel.getChildren().clear();
        deltaPanel.getChildren().add(new Label("变化对比"));
        ((Label) deltaPanel.getChildren().get(0)).getStyleClass().add("card-title");

        boolean compare = tbCompare.isSelected();
        morphBar.setVisible(compare && tbMorph.isSelected());
        morphBar.setManaged(compare && tbMorph.isSelected());
        morphSlider.setDisable(!(compare && tbMorph.isSelected()));

        // 仅一条记录时强制单期
        if (snaps.size() < 2) {
            if (compare) { tbSingle.setSelected(true); syncModeStyle(); compare = false; }
        }

        if (snaps.isEmpty()) {
            showPlaceholder(true, "暂无数据，请先录入健康记录");
            return;
        }

        Snap a = pick(cbA), b = pick(cbB);
        if (a == null) a = snaps.get(0);
        if (b == null) b = snaps.get(snaps.size() - 1);

        // 预先计算两期体型参数（3D / 2D 共用，亦供滑块拖动 / 窗口缩放重绘）
        BodyParams pa = BodyModel3D.fromRecord(a.rec, DBUtil.currentGender, DBUtil.currentHeight);
        BodyParams pb = BodyModel3D.fromRecord(b.rec, DBUtil.currentGender, DBUtil.currentHeight);
        lastA = pa; lastB = pb;

        try {
            if (!compare) {
                // 单期：显示期B（当前）
                if (support3D) {
                    spin.getChildren().add(BodyModel3D.buildBody(pb));
                } else {
                    draw2D(pb, null, false, "", "");
                }
                deltaPanel.getChildren().add(new Label("当前体型 · " + b.label));
                ((Label) deltaPanel.getChildren().get(0)).getStyleClass().add("card-title");
                addMetric("体重", b.num("weight"), "kg");
                addMetric("腰围", b.num("waist"), "cm");
                addMetric("体脂率", b.num("body_fat"), "%");
                addMetric("BMI", b.num("bmi"), "");
                addMetric("肌肉率", b.num("muscle_rate"), "%");
            } else if (tbSide.isSelected()) {
                // 并排：A 左 B 右
                if (support3D) {
                    Group ga = BodyModel3D.buildBody(pa);
                    Group gb = BodyModel3D.buildBody(pb);
                    ga.setTranslateX(-1.15);
                    gb.setTranslateX(1.15);
                    spin.getChildren().addAll(ga, gb);
                    overlayA.setText("期A(旧) " + a.label);
                    overlayB.setText("期B(新) " + b.label);
                    overlayA.setVisible(true); overlayB.setVisible(true);
                } else {
                    draw2D(pa, pb, true, a.label, b.label);
                }
                buildDelta(a, b);
            } else {
                // 形变：按滑块在 A↔B 之间插值
                double t = morphSlider.getValue();
                BodyParams p = BodyModel3D.lerpParams(pa, pb, t);
                if (support3D) {
                    spin.getChildren().add(BodyModel3D.buildBody(p));
                } else {
                    draw2D(p, null, false, "", "");
                }
                buildDelta(a, b);
            }
        } catch (Exception ex) {
            DBUtil.logError("BodyShape3DPanel.rebuild", ex);
            Alert al = new Alert(Alert.AlertType.ERROR, "3D 模型构建失败: " + ex.getMessage(), ButtonType.OK);
            al.setTitle("渲染异常");
            al.showAndWait();
        }
    }

    private Snap pick(ComboBox<String> cb) {
        String v = cb.getValue();
        if (v == null) return null;
        for (Snap s : snaps) if (s.label.equals(v)) return s;
        return null;
    }

    /** 2D 降级绘制：用 Canvas 画出体型剪影（单期 or 并排对比）。任何环境均可显示。 */
    private void draw2D(BodyParams pa, BodyParams pb, boolean sideBySide, String labelA, String labelB) {
        sub.setVisible(false);
        canvas2D.setVisible(true);
        canvas2D.setManaged(true);
        GraphicsContext g = canvas2D.getGraphicsContext2D();
        double w = viewStack.getWidth() > 1 ? viewStack.getWidth() : 640;
        double h = viewStack.getHeight() > 1 ? viewStack.getHeight() : 480;
        canvas2D.setWidth(w);
        canvas2D.setHeight(h);
        g.clearRect(0, 0, w, h);
        // 深色背景，与 3D 视图风格一致
        g.setFill(Color.web("#0E2A33"));
        g.fillRect(0, 0, w, h);
        // 地面线
        double baseY = h * 0.92;
        double figH = h * 0.80;
        if (sideBySide && pb != null) {
            BodyModel2D.draw(g, pa, w * 0.28, baseY, figH);
            BodyModel2D.draw(g, pb, w * 0.72, baseY, figH);
            overlayA.setText("期A(旧) " + labelA);
            overlayB.setText("期B(新) " + labelB);
            overlayA.setVisible(true); overlayB.setVisible(true);
        } else {
            BodyModel2D.draw(g, pa, w * 0.5, baseY, figH);
            overlayA.setVisible(false); overlayB.setVisible(false);
        }
    }

    private void buildDelta(Snap a, Snap b) {
        deltaPanel.getChildren().clear();
        Label t = new Label("变化对比 (期A → 期B)");
        t.getStyleClass().add("card-title");
        deltaPanel.getChildren().add(t);
        addDeltaRow("体重", a.num("weight"), b.num("weight"), "kg", true);
        addDeltaRow("腰围", a.num("waist"), b.num("waist"), "cm", true);
        addDeltaRow("体脂率", a.num("body_fat"), b.num("body_fat"), "%", true);
        addDeltaRow("BMI", a.num("bmi"), b.num("bmi"), "", true);
        addDeltaRow("肌肉率", a.num("muscle_rate"), b.num("muscle_rate"), "%", false);
    }

    private void addMetric(String name, double v, String unit) {
        Label l = new Label(name + ": " + (v > 0 ? String.format("%.1f", v) + unit : "—"));
        l.getStyleClass().add("muted");
        deltaPanel.getChildren().add(l);
    }
    private void addDeltaRow(String name, double av, double bv, String unit, boolean lowerBetter) {
        String aS = av > 0 ? String.format("%.1f", av) : "—";
        String bS = bv > 0 ? String.format("%.1f", bv) : "—";
        double diff = (av > 0 && bv > 0) ? bv - av : 0;
        String arrow = diff > 0.05 ? "▲" : (diff < -0.05 ? "▼" : "＝");
        String diffS = (av > 0 && bv > 0) ? String.format("%s%.1f%s", arrow, diff, unit) : "";
        // 颜色：对体重/腰围/体脂/BMI，下降=改善(绿)；肌肉率上升=改善(绿)
        boolean improve = lowerBetter ? diff < -0.05 : diff > 0.05;
        Color c = (av <= 0 || bv <= 0) ? Color.web("#B0BEC5")
                : (improve ? Color.web("#66BB6A") : Color.web("#EF5350"));
        HBox row = new HBox(6);
        Label ln = new Label(name); ln.setMinWidth(64); ln.getStyleClass().add("muted");
        Label lv = new Label(aS + " → " + bS); lv.setMinWidth(120);
        Label ld = new Label(diffS); ld.setTextFill(c); ld.setFont(Font.font(13));
        row.getChildren().addAll(ln, lv, ld);
        deltaPanel.getChildren().add(row);
    }
}
