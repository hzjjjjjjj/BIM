package com.bmi.ui.user;

import com.bmi.db.DBUtil;
import com.bmi.ui.ReportDialog;
import com.bmi.util.ImageUtil;
import com.bmi.util.AllergenKB;
import com.bmi.util.FoodAliasKB;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.scene.image.*;
import javafx.scene.Scene;
import javafx.stage.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;

import com.github.sarxos.webcam.Webcam;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.Map;
import java.util.Base64;
import javax.imageio.ImageIO;

/** 饮食管理面板 — 录入饮食记录、AI 识图算热量、今日营养汇总(饼图)、今日饮食记录 */
public class DietPanel extends VBox {
    private final ComboBox<String> cbMealType = new ComboBox<>();
    private final ComboBox<String> cbFood = new ComboBox<>();
    private final TextField tfGrams = new TextField("100");
    private final Label lblSummary = new Label("今日汇总: 暂无数据");
    private final PieChart pie = new PieChart();
    private final Map<String, String[]> foodData = new LinkedHashMap<>();
    private final TableView<String[]> todayTable = new TableView<>();
    private final ObservableList<String[]> todayData = FXCollections.observableArrayList();
    private String reportContent = "";

    // 识图结果区
    private final VBox resultsBox = new VBox(8);
    private final Label lblResultHint = new Label("点击上方「📷 上传识图」或「📸 摄像头拍照」，AI 会自动识别食物中的热量。");
    private final ObservableList<FoodItem> visionItems = FXCollections.observableArrayList();
    private final Button btnAddVision = new Button("加入今日饮食");
    private final Button btnSelectAll = new Button("全选/取消");
    /** 最近一次上传/拍照的原始图片字节，供缩略图与草稿入库使用 */
    private byte[] lastUploadedImage = null;

    // 食物图库（用户端浏览带图食物并一键加入）
    private final FlowPane galleryPane = new FlowPane(18, 18);
    private final TextField tfGallerySearch = new TextField();
    private final Label lblGalleryCount = new Label();
    private final VBox galleryCard = new VBox(10);
    private List<DBUtil.FoodRow> galleryAll = new ArrayList<>();

    public DietPanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        cbMealType.getItems().addAll("早餐", "午餐", "晚餐", "加餐");
        cbMealType.setValue("早餐");
        tfGrams.setPrefWidth(80);

        // 选菜时克数自动同步为该食物的标准份量，不必每次手改
        cbFood.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) {
                String[] f = foodData.get(nv);
                if (f != null && f.length > 5 && !f[5].isEmpty()) tfGrams.setText(f[5]);
            }
        });

        HBox input = new HBox(10);
        input.setAlignment(Pos.CENTER_LEFT);
        Label l1 = new Label("餐次:"); l1.getStyleClass().add("sub-title");
        Label l2 = new Label("食物:"); l2.getStyleClass().add("sub-title");
        Label l3 = new Label("食用量(g):"); l3.getStyleClass().add("sub-title");
        Button btnAdd = new Button("记录饮食");
        btnAdd.getStyleClass().add("button-primary");
        input.getChildren().addAll(l1, cbMealType, l2, cbFood, l3, tfGrams, btnAdd);

        HBox btnRow2 = new HBox(10);
        btnRow2.setAlignment(Pos.CENTER_LEFT);
        Button btnUpload = new Button("📷 上传识图");
        btnUpload.getStyleClass().add("button-accent");
        Button btnCam = new Button("📸 摄像头拍照");
        btnCam.getStyleClass().add("button-ghost");
        btnRow2.getChildren().addAll(btnUpload, btnCam);

        VBox inputCard = new VBox(10);
        inputCard.getStyleClass().add("card");
        Label t1 = new Label("饮食记录录入");
        t1.getStyleClass().add("card-title");
        inputCard.getChildren().addAll(t1, input, btnRow2);

        lblSummary.getStyleClass().add("card-title");
        pie.setTitle("营养素占比");
        pie.setLabelsVisible(true);
        pie.setPrefSize(320, 240);

        VBox left = new VBox(8, lblSummary, pie);
        left.setAlignment(Pos.TOP_LEFT);

        buildTodayTable();
        VBox right = new VBox(8, new Label("今日饮食记录") {{ getStyleClass().add("card-title"); }}, todayTable);
        VBox.setVgrow(todayTable, Priority.ALWAYS);
        right.setAlignment(Pos.TOP_LEFT);

        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getChildren().addAll(left, right);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox summaryHeader = new HBox(10);
        summaryHeader.setAlignment(Pos.CENTER_LEFT);
        Label t2 = new Label("今日营养汇总");
        t2.getStyleClass().add("card-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnReport = new Button("查看每日营养报告");
        btnReport.getStyleClass().add("button-primary");
        btnReport.setOnAction(e -> showReportDialog("每日营养报告", reportContent));
        summaryHeader.getChildren().addAll(t2, sp, btnReport);

        VBox summaryCard = new VBox(10);
        summaryCard.getStyleClass().add("card");
        summaryCard.getChildren().addAll(summaryHeader, top);
        VBox.setVgrow(summaryCard, Priority.ALWAYS);

        // 识图结果卡片
        lblResultHint.getStyleClass().add("hint");
        btnAddVision.getStyleClass().add("button-primary");
        btnAddVision.setDisable(true);
        btnSelectAll.getStyleClass().add("button-ghost");
        btnSelectAll.setDisable(true);
        HBox resultActions = new HBox(10, btnSelectAll, btnAddVision);
        resultActions.setAlignment(Pos.CENTER_LEFT);
        VBox resultCard = new VBox(10);
        resultCard.getStyleClass().add("card");
        Label t3 = new Label("AI 识图结果");
        t3.getStyleClass().add("card-title");
        resultCard.getChildren().addAll(t3, lblResultHint, resultsBox, resultActions);

        getChildren().addAll(inputCard, resultCard, galleryCard, summaryCard);
        VBox.setVgrow(summaryCard, Priority.ALWAYS);

        btnAdd.setOnAction(e -> addDietRecord());
        btnUpload.setOnAction(e -> uploadAndRecognize());
        btnCam.setOnAction(e -> captureAndRecognize());
        btnAddVision.setOnAction(e -> addVisionItems());
        btnSelectAll.setOnAction(e -> toggleSelectAll());
        buildFoodGallery();
        loadFoods();
        loadGallery();
        refreshSummary();

        // 切换/打开本页时重新拉取食物库：管理员导入/新增的食物能立即在用户端可见，
        // 避免面板仅在启动时加载一次导致"导入后用户端看不到"的错觉
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) { loadFoods(); loadGallery(); }
        });
    }

    // ===================== 食物图库（用户端） =====================
    private void buildFoodGallery() {
        Label title = new Label("食物图库");
        title.getStyleClass().add("card-title");

        tfGallerySearch.setPromptText("搜索食物名称…");
        tfGallerySearch.setPrefWidth(260);
        tfGallerySearch.getStyleClass().add("text-field");
        tfGallerySearch.textProperty().addListener((o, ov, nv) -> filterGallery(nv));
        lblGalleryCount.getStyleClass().add("text-muted");

        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        searchRow.getChildren().addAll(new Label("🔍"), tfGallerySearch, sp, lblGalleryCount);

        ScrollPane scroll = new ScrollPane(galleryPane);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(300);
        scroll.setPannable(true); // 支持鼠标拖动滚动图库
        scroll.setStyle("-fx-background: transparent; -fx-border-width: 0;");
        // 滚轮滚动图库：固定步长、不与主内容区抢占
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            double viewportH = scroll.getViewportBounds().getHeight();
            double contentH = galleryPane.getBoundsInLocal().getHeight();
            double scrollable = contentH - viewportH;
            if (scrollable > 0 && viewportH > 0) {
                double sign = Math.signum(e.getDeltaY());
                double frac = sign * 64.0 / scrollable;
                scroll.setVvalue(Math.max(0, Math.min(1, scroll.getVvalue() - frac)));
                e.consume();
            }
        });

        galleryCard.getStyleClass().add("card");
        galleryCard.getChildren().addAll(title, searchRow, scroll);
    }

    private void loadGallery() {
        galleryAll = DBUtil.getFoodsWithImage();
        renderGallery(galleryAll);
    }

    private void renderGallery(List<DBUtil.FoodRow> list) {
        galleryPane.getStyleClass().add("food-gallery");
        galleryPane.getChildren().clear();
        if (list == null || list.isEmpty()) {
            Label empty = new Label("暂无食物数据");
            empty.getStyleClass().add("hint");
            galleryPane.getChildren().add(empty);
            lblGalleryCount.setText("");
            return;
        }
        for (DBUtil.FoodRow fr : list) galleryPane.getChildren().add(buildFoodCard(fr));
        lblGalleryCount.setText("共 " + list.size() + " 项");
    }

    private void filterGallery(String kw) {
        if (kw == null || kw.trim().isEmpty()) {
            renderGallery(galleryAll);
            return;
        }
        String k = kw.trim().toLowerCase();
        List<DBUtil.FoodRow> filtered = new ArrayList<>();
        for (DBUtil.FoodRow fr : galleryAll) {
            if (fr.name() != null && fr.name().toLowerCase().contains(k)) filtered.add(fr);
        }
        renderGallery(filtered);
    }

    private VBox buildFoodCard(DBUtil.FoodRow fr) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("food-card");

        // 圆角缩略图（有图显示图片，无图显示占位）
        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("food-thumb");
        thumb.setPrefSize(120, 120);
        thumb.setMaxSize(120, 120);
        if (fr.image() != null && fr.image().length > 0) {
            ImageView iv = new ImageView(ImageUtil.byteArrayToImage(fr.image()));
            iv.setFitWidth(116);
            iv.setFitHeight(116);
            iv.setPreserveRatio(true);
            thumb.getChildren().add(iv);
        } else {
            Label ph = new Label("无图");
            ph.setStyle("-fx-text-fill:#8A97A5; -fx-font-size:11px;");
            thumb.getChildren().add(ph);
        }

        Label name = new Label(fr.name());
        name.getStyleClass().add("food-name");
        name.setWrapText(true);
        name.setAlignment(Pos.CENTER);
        name.setMaxWidth(140);

        Label cal = new Label(fr.cal() + " kcal / 100g");
        cal.getStyleClass().add("food-cal");

        HBox macros = new HBox(6);
        macros.setAlignment(Pos.CENTER);
        macros.getChildren().addAll(
                macroChip("蛋白", f1(fr.protein()), "food-chip-p"),
                macroChip("碳水", f1(fr.carbs()), "food-chip-c"),
                macroChip("脂肪", f1(fr.fat()), "food-chip-f"));

        Button add = new Button("＋ 加入");
        add.getStyleClass().add("button-primary");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> addFoodFromGallery(fr));

        card.getChildren().addAll(thumb, name, cal, macros, add);
        return card;
    }

    private Label macroChip(String tag, String val, String cls) {
        Label chip = new Label(tag + " " + val);
        chip.getStyleClass().addAll("food-chip", cls);
        return chip;
    }

    /** 从图库一键加入今日饮食：用量取该食物的标准份量（克）。 */
    private void addFoodFromGallery(DBUtil.FoodRow fr) {
        String mealType = cbMealType.getValue();
        double grams = (fr.defaultGrams() > 0) ? fr.defaultGrams() : 100;
        double ratio = grams / 100.0;
        int calories = (int) (fr.cal() * ratio);
        if (DBUtil.saveDietRecord(mealType, fr.name() + "(" + f0(grams) + "g)",
                calories, fr.protein() * ratio, fr.carbs() * ratio, fr.fat() * ratio)) {
            refreshSummary();
            alert("已加入 " + fr.name() + " (" + f0(grams) + "g)");
        } else {
            alert("加入失败");
        }
    }

    // ===================== 识图逻辑 =====================
    private static final String VISION_PROMPT = "你是营养识别助手。请识别图片中的食物，估算每种食物的重量(克)以及热量(千卡)、蛋白质(克)、碳水(克)、脂肪(克)。"
            + "严格只按如下格式每行返回一条，不要解释、不要序号、不要 markdown：\n"
            + "食物名|重量克|热量kcal|蛋白质g|碳水g|脂肪g\n"
            + "例如：宫保鸡丁|250|420|28|18|22\n"
            + "苹果|150|78|0.4|21|0.3\n"
            + "香蕉|120|107|1.3|27|0.4\n"
            + "番茄|120|22|1.0|4.5|0.2\n"
            + "米饭|200|232|4.3|51|0.5\n"
            + "若图中无食物或无法识别，返回一行：未知|0|0|0|0|0\n"
            + "品牌识别：若图片中可见品牌、包装或门店字样（如「塔斯汀」「肯德基」「麦当劳」），请务必保留品牌名，"
            + "例如「塔斯汀香辣鸡腿堡」而非泛化的「鸡肉汉堡」或「汉堡」。";

    private void showResults(List<FoodItem> items) {
        resultsBox.getChildren().clear();
        visionItems.setAll(items);
        if (items.isEmpty()) {
            lblResultHint.setText("未能从图片中识别出食物，请换一张更清晰的照片，或在上方手动录入。");
            btnAddVision.setDisable(true);
            btnSelectAll.setDisable(true);
            return;
        }
        lblResultHint.setText("已识别 " + items.size() + " 项，可微调克数 / 餐次后一键加入：");
        for (FoodItem it : items) resultsBox.getChildren().add(buildResultRow(it));
        btnAddVision.setDisable(false);
        btnSelectAll.setDisable(false);
    }

    private HBox buildResultRow(FoodItem it) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("vision-item");
        row.getChildren().add(thumbNode(it));

        CheckBox chk = new CheckBox();
        chk.setSelected(true);
        it.selected = true;
        chk.selectedProperty().addListener((o, ov, nv) -> it.selected = nv);

        Label name = new Label(it.name);
        name.getStyleClass().add("sub-title");
        name.setPrefWidth(96);

        String allergenHit = AllergenKB.match(it.name, DBUtil.currentAllergies);
        Label status;
        if (allergenHit != null) {
            status = new Label("⚠ 已拦截(过敏源): " + allergenHit);
            status.setStyle("-fx-text-fill:#C0392B; -fx-font-weight:bold;");
        } else {
            status = new Label(it.matched != null ? "已匹配: " + it.matched.name() : "待管理员审核");
            status.getStyleClass().add((it.matched != null || it.approved) ? "sub-title" : "text-muted");
        }

        TextField gramsTf = new TextField(String.valueOf(it.grams));
        gramsTf.setPrefWidth(56);
        it.gramsField = gramsTf;
        Label cal = new Label(it.cal + " kcal（约" + it.grams + "g）");
        Label macro = new Label(String.format("P%.0f C%.0f F%.0f", it.protein, it.carbs, it.fat));
        macro.getStyleClass().add("text-muted");
        ComboBox<String> meal = new ComboBox<>();
        meal.getItems().addAll("早餐", "午餐", "晚餐", "加餐");
        meal.setValue("午餐");
        it.mealBox = meal;

        row.getChildren().addAll(chk, name, status, new Label("g:"), gramsTf, cal, macro, new Label("餐次:"), meal);

        // 过敏源命中：整行标红警示并禁用勾选，从 AI 识图路径硬拦截，避免误食
        if (allergenHit != null) {
            row.setStyle("-fx-background-color:#FDECEA; -fx-border-color:#E0A9A0; -fx-border-radius:6; -fx-padding:4;");
            chk.setSelected(false);
            chk.setDisable(true);
            it.selected = false;
        }

        // 注：新食物不再由普通用户在本人界面"确认入库"——写全局食物库属管理动作，
        // 统一交由管理员在「待确认食物」审核队列中审批（见 FoodManagePanel）。
        // 用户侧仅可把识别结果加入自己的今日饮食。
        return row;
    }

    /** 结果行缩略图：优先命中食物的库图，其次本次上传图，再否则占位节点。 */
    private Node thumbNode(FoodItem it) {
        byte[] bytes = (it.matched != null && it.matched.image() != null && it.matched.image().length > 0)
                ? it.matched.image() : lastUploadedImage;
        if (bytes != null && bytes.length > 0) {
            ImageView iv = new ImageView(ImageUtil.byteArrayToImage(bytes));
            iv.setFitWidth(40);
            iv.setFitHeight(40);
            iv.setPreserveRatio(true);
            return iv;
        }
        return placeholderNode(40);
    }

    /** 无图占位节点（灰色圆角方块 + 「无图」）。 */
    private Node placeholderNode(int size) {
        StackPane ph = new StackPane(new Label("无图"));
        ph.setPrefSize(size, size);
        ph.setStyle("-fx-background-color:#E3E8EE; -fx-background-radius:6; "
                + "-fx-border-color:#C7D0DA; -fx-border-radius:6;");
        ph.getChildren().get(0).setStyle("-fx-text-fill:#8A97A5; -fx-font-size:10px;");
        return ph;
    }

    private void toggleSelectAll() {
        boolean all = visionItems.stream().allMatch(i -> i.selected);
        visionItems.forEach(i -> i.selected = !all);
        resultsBox.getChildren().clear();
        for (FoodItem it : visionItems) resultsBox.getChildren().add(buildResultRow(it));
    }

    private void uploadAndRecognize() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择食物照片");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
        Window win = getScene() != null ? getScene().getWindow() : null;
        File f = fc.showOpenDialog(win);
        if (f == null) return;
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            recognize(bytes);
        } catch (Exception ex) {
            alert("读取图片失败: " + ex.getMessage());
        }
    }

    private void captureAndRecognize() {
        try {
            final Webcam webcam = Webcam.getDefault();
            if (webcam == null) { alert("未检测到摄像头设备"); return; }
            final Stage stage = new Stage();
            stage.setTitle("摄像头拍照");
            ImageView view = new ImageView();
            view.setFitWidth(480); view.setFitHeight(360); view.setPreserveRatio(true);
            Button snap = new Button("📸 拍照识图");
            snap.getStyleClass().add("button-primary");
            VBox root = new VBox(10, view, snap);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(12));
            root.getStyleClass().add("card");
            stage.setScene(new Scene(root));
            final boolean[] closed = {false};
            new Thread(() -> {
                try {
                    if (!webcam.isOpen()) webcam.open();
                    while (!closed[0]) {
                        BufferedImage img = webcam.getImage();
                        if (img != null) {
                            final Image fx = ImageUtil.toFXImage(img);
                            Platform.runLater(() -> view.setImage(fx));
                        }
                        Thread.sleep(120);
                    }
                } catch (Throwable t) {
                    Platform.runLater(() -> alert("摄像头打开失败: " + t.getMessage()));
                }
            }).start();
            snap.setOnAction(ev -> {
                try {
                    BufferedImage img = webcam.getImage();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "png", baos);
                    closed[0] = true;
                    webcam.close();
                    stage.close();
                    recognize(baos.toByteArray());
                } catch (Exception ex) {
                    alert("拍照失败: " + ex.getMessage());
                }
            });
            stage.setOnCloseRequest(e -> { closed[0] = true; try { webcam.close(); } catch (Exception ignore) {} });
            stage.show();
        } catch (Throwable t) {
            alert("摄像头不可用: " + t.getMessage());
        }
    }

    private void recognize(byte[] imageBytes) {
        Map<String, String> cfg = DBUtil.getAIApiConfig();
        String apiKey = cfg.getOrDefault("api_key", "");
        if (apiKey.isEmpty()) { alert("请先在「管理员端 → API 配置」中填写 API Key"); return; }
        String vision = cfg.getOrDefault("vision_model", "glm-4v-flash");
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        lblResultHint.setText("AI 正在识别中…");
        btnAddVision.setDisable(true);
        btnSelectAll.setDisable(true);
        new Thread(() -> {
            try {
                String resp = DBUtil.callVision(apiKey, vision, VISION_PROMPT, b64);
                List<FoodItem> items = parseVisionResult(resp);
                long uploadHash = ImageUtil.perceptualHash(ImageUtil.bufferedImageFromBytes(imageBytes));
                for (FoodItem it : items) {
                    // 同物异名归一并统一口径：如 AI 返回"西红柿/马铃薯"归一到库内"番茄/土豆"，
                    // 提高 matchFood 命中率，避免同一食物因别名被误建草稿（整词精确匹配，绝不子串替换）
                    it.name = FoodAliasKB.canonical(it.name);
                    DBUtil.FoodRow matched = DBUtil.matchFood(it.name, uploadHash);
                    if (matched != null) {
                        it.matched = matched;
                        // 库里该食物无图 → 用本次上传图补充（"相似则加上"）
                        if (matched.image() == null || matched.image().length == 0) {
                            DBUtil.updateFoodImage(matched.id(), imageBytes);
                        }
                    } else {
                        // 库里没有 → 建草稿待确认（按 AI 每份克数折算成每100g，口径与食物库一致）
                        it.draftId = DBUtil.saveDraftFood(it.name, it.grams, it.cal, it.protein, it.carbs, it.fat, imageBytes);
                    }
                }
                lastUploadedImage = imageBytes;
                Platform.runLater(() -> showResults(items));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblResultHint.setText("识别失败: " + ex.getMessage());
                    btnAddVision.setDisable(true);
                    btnSelectAll.setDisable(true);
                });
            }
        }).start();
    }

    /**
     * 解析视觉模型返回的文本为食物列表。
     * 原实现要求严格 "名|重|卡|蛋|碳|脂" 且数值必须为纯数字，模型一旦带单位（如 200g/320kcal）
     * 或加前缀（约/大概），Double.parseDouble 抛异常导致整行被丢弃，表现为"未能识别"。
     * 现改为：容忍单位/前缀（提取首个数字）、跳过 markdown 表格分隔行（|---|---|）、
     * 字段数 >=6 即可（多余列忽略），让解析器与具体模型的输出格式解耦。
     */
    private List<FoodItem> parseVisionResult(String text) {
        List<FoodItem> list = new ArrayList<>();
        if (text == null) return list;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || !line.contains("|")) continue;
            // 跳过 markdown 表格分隔行 |---|---|
            if (line.replace("|", "").replace("-", "").trim().isEmpty()) continue;
            String[] p = line.split("\\|");
            if (p.length < 6) continue;
            String name = p[0].trim();
            if (name.isEmpty() || "未知".equals(name) || "食物名".equals(name)) continue;
            try {
                FoodItem it = new FoodItem();
                it.name = name;
                it.grams = (int) Math.max(0, firstNumber(p[1]));
                it.cal = (int) Math.max(0, firstNumber(p[2]));
                it.protein = Math.max(0, firstNumber(p[3]));
                it.carbs = Math.max(0, firstNumber(p[4]));
                it.fat = Math.max(0, firstNumber(p[5]));
                list.add(it);
            } catch (Exception ignore) {}
        }
        return list;
    }

    /** 从字段文本提取首个数字，容忍 "200g"、"约320kcal"、"15.5" 等带单位/前缀写法；无数字返回 0。 */
    private static double firstNumber(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[-+]?\\d+(\\.\\d+)?").matcher(s);
        return m.find() ? Double.parseDouble(m.group()) : 0;
    }

    private void addVisionItems() {
        int added = 0;
        List<String> blocked = new ArrayList<>();
        for (FoodItem it : visionItems) {
            // 过敏源命中：AI 识图路径硬拦截，绝不直接加入（安全优先）
            String hit = AllergenKB.match(it.name, DBUtil.currentAllergies);
            if (hit != null) {
                blocked.add(it.name + "（过敏源：" + hit + "）");
                continue;
            }
            if (!it.selected) continue;
            int newGrams;
            try { newGrams = (int) Math.max(1, Double.parseDouble(it.gramsField.getText().trim())); }
            catch (Exception ex) { newGrams = it.grams > 0 ? it.grams : 1; }
            double ratio = it.grams > 0 ? (double) newGrams / it.grams : 1.0;
            int cal = (int) Math.round(it.cal * ratio);
            double p = it.protein * ratio, c = it.carbs * ratio, f = it.fat * ratio;
            String meal = it.mealBox != null ? it.mealBox.getValue() : "午餐";
            if (DBUtil.saveDietRecord(meal, it.name + "(" + newGrams + "g)", cal, p, c, f)) added++;
        }
        StringBuilder msg = new StringBuilder();
        if (added > 0) msg.append("已加入 ").append(added).append(" 项饮食记录。\n");
        if (!blocked.isEmpty()) {
            msg.append("⚠ 已阻止 ").append(blocked.size()).append(" 项过敏食物加入今日饮食（过敏源：")
               .append(String.join("、", blocked)).append("），安全优先。\n如需记录请在上方「饮食记录录入」手动添加。");
        }
        if (msg.length() > 0) {
            alert(msg.toString());
            if (added > 0) {
                refreshSummary();
                visionItems.clear();
                resultsBox.getChildren().clear();
                lblResultHint.setText("点击上方「📷 上传识图」或「📸 摄像头拍照」，AI 会自动识别食物中的热量。");
                btnAddVision.setDisable(true);
                btnSelectAll.setDisable(true);
            }
        } else {
            alert("没有可加入的项");
        }
    }

    private static class FoodItem {
        String name; int grams; int cal; double protein, carbs, fat;
        boolean selected = true;
        TextField gramsField;
        ComboBox<String> mealBox;
        DBUtil.FoodRow matched = null;   // 命中的本地食物（含图）
        int draftId = -1;               // 草稿 id（未命中时新建）
        boolean approved = false;       // 草稿是否已被审核通过
    }

    // ===================== 原有录入与汇总逻辑 =====================
    private void buildTodayTable() {
        String[] cols = {"餐次", "食物", "热量", "蛋白质", "碳水", "脂肪"};
        for (int i = 0; i < cols.length; i++) {
            final int idx = i;
            TableColumn<String[], String> c = new TableColumn<>(cols[i]);
            c.setCellValueFactory(cb -> new ReadOnlyStringWrapper(cb.getValue()[idx]));
            todayTable.getColumns().add(c);
        }
        todayTable.setItems(todayData);
    }

    private void loadFoods() {
        cbFood.getItems().clear();
        foodData.clear();
        for (String[] food : DBUtil.getAllFoods()) {
            String name = food[0];
            foodData.put(name, food);
            cbFood.getItems().add(name);
        }
        if (!cbFood.getItems().isEmpty()) cbFood.setValue(cbFood.getItems().get(0));
    }

    private void addDietRecord() {
        String mealType = cbMealType.getValue();
        String foodName = cbFood.getValue();
        if (foodName == null) { alert("请选择食物"); return; }
        try {
            double grams = Double.parseDouble(tfGrams.getText().trim());
            if (grams <= 0 || grams > 5000) { alert("食用量范围 1-5000g"); return; }
            String[] food = foodData.get(foodName);
            double ratio = grams / 100.0;
            int calories = (int) (Integer.parseInt(food[1]) * ratio);
            double protein = Double.parseDouble(food[2]) * ratio;
            double carbs = Double.parseDouble(food[3]) * ratio;
            double fat = Double.parseDouble(food[4]) * ratio;
            if (DBUtil.saveDietRecord(mealType, foodName + "(" + f0(grams) + "g)", calories, protein, carbs, fat)) {
                alert("饮食记录保存成功! 热量: " + calories + " kcal");
                refreshSummary();
            } else {
                alert("饮食记录保存失败");
            }
        } catch (NumberFormatException e) {
            alert("请输入有效数字");
        }
    }

    private void refreshSummary() {
        int[] diet = DBUtil.getTodayDietSummary();
        int totalCal = diet[0];
        double protein = diet[1] / 100.0;
        double carbs = diet[2] / 100.0;
        double fat = diet[3] / 100.0;

        Map<String, Object> latest = DBUtil.getLatestHealthRecord();
        double tdee = latest != null ? num(latest, "tdee") : 1800;
        double recProtein = latest != null ? num(latest, "weight") * 1.2 : 60;
        double recCarbs = tdee * 0.5 / 4;
        double recFat = tdee * 0.3 / 9;

        lblSummary.setText(String.format("今日汇总: 总热量 %d kcal / 推荐 %d kcal | 蛋白质 %.1fg | 碳水 %.1fg | 脂肪 %.1fg",
                totalCal, (int) tdee, protein, carbs, fat));

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("蛋白质 " + f1(protein * 4), protein * 4),
                new PieChart.Data("碳水 " + f1(carbs * 4), carbs * 4),
                new PieChart.Data("脂肪 " + f1(fat * 9), fat * 9));
        pie.setData(pieData);

        todayData.setAll(DBUtil.getTodayDietRecords());

        StringBuilder sb = new StringBuilder();
        sb.append("═══ 每日营养报告 ═══\n\n");
        sb.append("热量:\n");
        sb.append(String.format("  摄入 %d kcal / 推荐 %d kcal", totalCal, (int) tdee));
        if (totalCal > tdee * 1.1) sb.append("  [超标]");
        else if (totalCal < tdee * 0.7) sb.append("  [不足]");
        sb.append("\n\n");

        sb.append("蛋白质:\n");
        sb.append(String.format("  摄入 %.1fg / 推荐 %.1fg", protein, recProtein));
        if (protein < recProtein * 0.8) sb.append("  [不足]");
        sb.append("\n\n");

        sb.append("碳水:\n");
        sb.append(String.format("  摄入 %.1fg / 推荐 %.1fg", carbs, recCarbs));
        if (carbs > recCarbs * 1.3) sb.append("  [超标]");
        sb.append("\n\n");

        sb.append("脂肪:\n");
        sb.append(String.format("  摄入 %.1fg / 推荐 %.1fg", fat, recFat));
        if (fat > recFat * 1.3) sb.append("  [超标]");
        sb.append("\n\n");

        double totalNutrientCal = protein * 4 + carbs * 4 + fat * 9;
        if (totalNutrientCal > 0) {
            sb.append("营养素占比:\n");
            sb.append(String.format("  蛋白质: %.1f%%\n", protein * 4 / totalNutrientCal * 100));
            sb.append(String.format("  碳水:   %.1f%%\n", carbs * 4 / totalNutrientCal * 100));
            sb.append(String.format("  脂肪:   %.1f%%\n", fat * 9 / totalNutrientCal * 100));
        }
        reportContent = sb.toString();
    }

    private double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private static String f0(double v) { return String.format("%.0f", v); }
    private static String f1(double v) { return String.format("%.1f", v); }

    private void showReportDialog(String title, String content) {
        ReportDialog.showText(title, content);
    }

    /** 判断食物名是否命中当前用户过敏源（精确子串 + 知识库类别展开）；命中返回冲突词，否则 null。 */
    private String hitsAllergen(String foodName) {
        return AllergenKB.match(foodName, DBUtil.currentAllergies);
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }
}
