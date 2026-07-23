package com.bmi.ui.admin;

import com.bmi.db.DBUtil;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;

import java.util.Map;

/**
 * AI API 配置面板（JavaFX 8 重写 version1 AISystemPanel API 配置部分）
 * apiKey / model / endpoint 表单，调用 getAIApiConfig / saveAIApiConfig。
 */
public class ApiConfigPanel extends VBox {
    private final TextField tfApiKey = new TextField();
    private final TextField tfModel = new TextField("glm-4.7-flash");
    private final TextField tfVisionModel = new TextField("glm-4v-flash");
    private final TextField tfEndpoint = new TextField("https://open.bigmodel.cn/api/paas/v4");
    private final TextField tfProxyHost = new TextField();
    private final TextField tfProxyPort = new TextField();

    public ApiConfigPanel() {
        setSpacing(16);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: transparent;");

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label title = new Label("AI API 配置");
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(8, 4, 8, 4));
        for (TextField t : new TextField[]{tfApiKey, tfModel, tfEndpoint, tfProxyHost, tfProxyPort}) t.getStyleClass().add("text-field");
        tfApiKey.setPromptText("如 glm-4.7-flash 的 API Key");
        tfVisionModel.setPromptText("支持视觉的模型，如 glm-4v-flash / gpt-4o / qwen-vl");
        form.addRow(0, new Label("API Key:"), tfApiKey);
        form.addRow(1, new Label("模型名称:"), tfModel);
        form.addRow(2, new Label("视觉模型:"), tfVisionModel);
        form.addRow(3, new Label("接口地址:"), tfEndpoint);
        form.addRow(4, new Label("代理地址(可选):"), tfProxyHost);
        form.addRow(5, new Label("代理端口(可选):"), tfProxyPort);
        Label hint = new Label("配置智谱 GLM（OpenAI 兼容）API 信息；「视觉模型」用于饮食识图算热量（保存到数据库）。若网络需经代理才能访问外网，填写代理地址与端口。");
        hint.getStyleClass().add("hint");
        form.add(hint, 0, 6, 2, 1);
        card.getChildren().add(form);

        Button btnSave = new Button("保存配置");
        btnSave.getStyleClass().add("button-primary");
        btnSave.setOnAction(e -> save());
        HBox btnBox = new HBox(btnSave);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(6, 0, 0, 0));
        card.getChildren().add(btnBox);
        getChildren().add(card);

        loadConfig();
    }

    private void loadConfig() {
        Map<String, String> cfg = DBUtil.getAIApiConfig();
        if (cfg != null) {
            tfApiKey.setText(cfg.getOrDefault("api_key", ""));
            tfModel.setText(cfg.getOrDefault("model_name", "glm-4.7-flash"));
            tfVisionModel.setText(cfg.getOrDefault("vision_model", "glm-4v-flash"));
            tfEndpoint.setText(cfg.getOrDefault("endpoint_url", "https://open.bigmodel.cn/api/paas/v4"));
            tfProxyHost.setText(cfg.getOrDefault("proxy_host", ""));
            tfProxyPort.setText(cfg.getOrDefault("proxy_port", ""));
        }
    }

    private void save() {
        String apiKey = tfApiKey.getText().trim();
        String modelName = tfModel.getText().trim();
        String visionModel = tfVisionModel.getText().trim();
        String endpoint = tfEndpoint.getText().trim();
        String proxyHost = tfProxyHost.getText().trim();
        String proxyPort = tfProxyPort.getText().trim();
        if (modelName.isEmpty() || endpoint.isEmpty()) {
            warn("模型名称和接口地址不能为空");
            return;
        }
        if (DBUtil.saveAIApiConfig(apiKey, modelName, visionModel, endpoint, proxyHost, proxyPort)) {
            DBUtil.logAction("ADMIN", DBUtil.currentUsername, "更新API配置", modelName);
            info("API 配置已保存到数据库\n\n后续可在 Prompt 模板或健康顾问模块中调用此配置生成 AI 建议。");
        } else {
            err("保存失败，请检查数据库连接");
        }
    }

    private void info(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }

    private void warn(String m) {
        new Alert(Alert.AlertType.WARNING, m, ButtonType.OK).showAndWait();
    }

    private void err(String m) {
        new Alert(Alert.AlertType.ERROR, m, ButtonType.OK).showAndWait();
    }
}
