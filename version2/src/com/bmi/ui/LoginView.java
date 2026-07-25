package com.bmi.ui;

import com.bmi.App;
import com.bmi.db.DBUtil;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.*;

public class LoginView {
    private BorderPane root = new BorderPane();

    public LoginView() {
        // 左侧品牌渐变区
        StackPane left = new StackPane();
        left.getStyleClass().add("bg-gradient");
        left.setMinWidth(420);
        Label brand = new Label("BMI 体质评估\n与预测系统");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 30px; -fx-font-weight: bold; -fx-alignment: center; -fx-line-spacing: 6px;");
        brand.setWrapText(true);
        Label sub = new Label("健康数据 · 智能分析 · 科学管理");
        sub.setStyle("-fx-text-fill: #D6ECF2; -fx-font-size: 14px;");
        VBox leftBox = new VBox(14, brand, sub);
        leftBox.setAlignment(Pos.CENTER);
        left.getChildren().add(leftBox);
        root.setLeft(left);

        // 右侧表单卡片
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(380);
        card.setMaxHeight(520);

        TabPane tabPane = new TabPane();
        Tab tabLogin = new Tab("登录", createLoginPanel());
        Tab tabReg = new Tab("注册", createRegisterPanel());
        Tab tabOrg = new Tab("机构登录", createInstitutionPanel());
        tabLogin.setClosable(false);
        tabReg.setClosable(false);
        tabOrg.setClosable(false);
        tabPane.getTabs().addAll(tabLogin, tabReg, tabOrg);
        card.getChildren().add(tabPane);

        root.setCenter(card);
        BorderPane.setAlignment(card, Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #EAF3F7, #D6E6EC);");
    }

    private GridPane createLoginPanel() {
        GridPane p = new GridPane();
        p.setHgap(10);
        p.setVgap(10);
        p.setPadding(new Insets(10, 4, 4, 4));
        TextField tfUser = new TextField();
        tfUser.setPromptText("用户名");
        PasswordField pfPass = new PasswordField();
        pfPass.setPromptText("密码");
        Button btn = new Button("登 录");
        btn.getStyleClass().add("button-primary");
        btn.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("请输入账号密码登录");
        hint.getStyleClass().add("hint");

        p.add(hint, 0, 0, 2, 1);
        p.add(new Label("用户名:"), 0, 1);
        p.add(tfUser, 1, 1);
        p.add(new Label("密码:"), 0, 2);
        p.add(pfPass, 1, 2);
        p.add(btn, 0, 3, 2, 1);

        btn.setOnAction(e -> doLogin(tfUser.getText(), pfPass.getText()));
        pfPass.setOnAction(e -> doLogin(tfUser.getText(), pfPass.getText()));
        tfUser.setOnAction(e -> doLogin(tfUser.getText(), pfPass.getText()));
        return p;
    }

    private void doLogin(String u, String p) {
        u = u == null ? "" : u.trim();
        if (u.isEmpty() || p == null || p.isEmpty()) {
            alert(Alert.AlertType.WARNING, "提示", "请输入用户名和密码");
            return;
        }
        if (DBUtil.loginAdmin(u, p)) {
            App.currentUser = DBUtil.currentUsername;
            App.currentRole = "admin";
            App.showAdmin();
        } else if (DBUtil.loginUser(u, p)) {
            App.currentUser = DBUtil.currentUsername;
            App.currentRole = "user";
            App.showMain();
        } else {
            alert(Alert.AlertType.ERROR, "登录失败", "用户名或密码错误");
        }
    }

    private GridPane createRegisterPanel() {
        GridPane p = new GridPane();
        p.setHgap(10);
        p.setVgap(8);
        p.setPadding(new Insets(10, 4, 4, 4));
        TextField tfUser = new TextField();
        tfUser.setPromptText("用户名");
        PasswordField pf1 = new PasswordField();
        pf1.setPromptText("密码");
        PasswordField pf2 = new PasswordField();
        pf2.setPromptText("确认密码");
        ComboBox<String> cbGender = new ComboBox<>();
        cbGender.getItems().addAll("男", "女");
        cbGender.setValue("男");
        Spinner<Integer> spAge = new Spinner<>(5, 120, 25);
        TextField tfHeight = new TextField("170");
        TextField tfWeight = new TextField();
        tfWeight.setPromptText("如 65");
        TextField tfWaist = new TextField();
        tfWaist.setPromptText("可选，如 80");
        TextField tfAllergy = new TextField();
        tfAllergy.setPromptText("可选, 如 花生,海鲜,芒果");
        TextField tfChronic = new TextField();
        tfChronic.setPromptText("可选, 如 高血压,糖尿病");
        ComboBox<String> cbAct = new ComboBox<>();
        cbAct.getItems().addAll("久坐", "轻度", "中度", "重度", "极重度");
        cbAct.setValue("久坐");
        Button btn = new Button("注 册");
        btn.getStyleClass().add("button-accent");
        btn.setMaxWidth(Double.MAX_VALUE);

        int r = 0;
        p.add(new Label("用户名:"), 0, r);
        p.add(tfUser, 1, r++);
        p.add(new Label("密码:"), 0, r);
        p.add(pf1, 1, r++);
        p.add(new Label("确认密码:"), 0, r);
        p.add(pf2, 1, r++);
        p.add(new Label("性别:"), 0, r);
        p.add(cbGender, 1, r++);
        p.add(new Label("年龄:"), 0, r);
        p.add(spAge, 1, r++);
        p.add(new Label("身高(cm):"), 0, r);
        p.add(tfHeight, 1, r++);
        p.add(new Label("体重(kg):"), 0, r);
        p.add(tfWeight, 1, r++);
        p.add(new Label("腰围(cm):(可选)"), 0, r);
        p.add(tfWaist, 1, r++);
        p.add(new Label("活动等级:"), 0, r);
        p.add(cbAct, 1, r++);
        p.add(new Label("过敏源:"), 0, r);
        p.add(tfAllergy, 1, r++);
        p.add(new Label("慢性病:"), 0, r);
        p.add(tfChronic, 1, r++);
        p.add(btn, 0, r, 2, 1);

        btn.setOnAction(e -> doRegister(tfUser.getText(), pf1.getText(), pf2.getText(),
                cbGender.getValue(), spAge.getValue(), tfHeight.getText(), tfWeight.getText(),
                tfWaist.getText(), cbAct.getValue(), tfAllergy.getText(), tfChronic.getText()));
        return p;
    }

    private void doRegister(String user, String p1, String p2, String gender, int age, String h,
                            String w, String waistStr, String act, String allergy, String chronic) {
        user = user == null ? "" : user.trim();
        if (user.isEmpty() || p1 == null || p1.isEmpty()) {
            alert(Alert.AlertType.WARNING, "提示", "用户名和密码不能为空");
            return;
        }
        if (!p1.equals(p2)) {
            alert(Alert.AlertType.WARNING, "提示", "两次密码不一致");
            return;
        }
        double height;
        try {
            height = Double.parseDouble(h.trim());
            if (height < 100 || height > 250) {
                alert(Alert.AlertType.WARNING, "提示", "身高范围 100-250cm");
                return;
            }
        } catch (Exception ex) {
            alert(Alert.AlertType.WARNING, "提示", "身高请输入数字");
            return;
        }
        double weight;
        try {
            weight = Double.parseDouble(w.trim());
            if (weight < 20 || weight > 500) {
                alert(Alert.AlertType.WARNING, "提示", "体重范围 20-500kg");
                return;
            }
        } catch (Exception ex) {
            alert(Alert.AlertType.WARNING, "提示", "体重请输入数字");
            return;
        }
        Double waist = null;
        if (waistStr != null && !waistStr.trim().isEmpty()) {
            try {
                waist = Double.parseDouble(waistStr.trim());
                if (waist < 30 || waist > 200) {
                    alert(Alert.AlertType.WARNING, "提示", "腰围范围 30-200cm");
                    return;
                }
            } catch (Exception ex) {
                alert(Alert.AlertType.WARNING, "提示", "腰围请输入数字");
                return;
            }
        }
        if (DBUtil.registerUser(user, p1, gender, age, height, act, weight, waist, allergy, chronic)) {
            DBUtil.saveBaselineHealthRecord(user, weight, height, age, gender, act, waist);
            alert(Alert.AlertType.INFORMATION, "成功", "注册成功! 请登录");
        } else {
            alert(Alert.AlertType.ERROR, "失败", "注册失败 (用户名可能已存在)");
        }
    }

    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t, msg, ButtonType.OK);
        a.setTitle(title);
        a.showAndWait();
    }

    private VBox createInstitutionPanel() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(10, 4, 4, 4));

        // 分段切换: 登录 / 申请入驻
        ToggleGroup tg = new ToggleGroup();
        ToggleButton tbLogin = new ToggleButton("机构登录");
        ToggleButton tbApply = new ToggleButton("申请入驻");
        tbLogin.setToggleGroup(tg);
        tbApply.setToggleGroup(tg);
        tbLogin.setSelected(true);
        tbLogin.getStyleClass().add("toggle-on");
        tbApply.getStyleClass().add("toggle-off");
        HBox switchBar = new HBox(0, tbLogin, tbApply);
        switchBar.setMaxWidth(Double.MAX_VALUE);

        VBox loginPane = buildInstitutionLoginPane();
        VBox applyPane = buildInstitutionApplyPane();
        // 用滚动面板包裹申请表单, 字段较多时也能看到底部「确认提交申请」按钮
        ScrollPane applyScroll = new ScrollPane(applyPane);
        applyScroll.setFitToWidth(true);
        applyScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        tg.selectedToggleProperty().addListener((ob, o, n) -> {
            boolean login = n == tbLogin;
            loginPane.setVisible(login);
            loginPane.setManaged(login);
            applyScroll.setVisible(!login);
            applyScroll.setManaged(!login);
            tbLogin.getStyleClass().removeAll("toggle-on", "toggle-off");
            tbApply.getStyleClass().removeAll("toggle-on", "toggle-off");
            tbLogin.getStyleClass().add(login ? "toggle-on" : "toggle-off");
            tbApply.getStyleClass().add(login ? "toggle-off" : "toggle-on");
        });

        box.getChildren().addAll(switchBar, loginPane, applyScroll);
        return box;
    }

    private VBox buildInstitutionLoginPane() {
        VBox p = new VBox(10);
        TextField tfCode = new TextField();
        tfCode.setPromptText("机构编码 (如 ORG-1024)");
        PasswordField pfPass = new PasswordField();
        pfPass.setPromptText("密码");
        Button btn = new Button("登 录");
        btn.getStyleClass().add("button-primary");
        btn.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("医疗机构请使用管理员下发的机构编码登录");
        hint.getStyleClass().add("hint");

        btn.setOnAction(e -> doInstitutionLogin(tfCode.getText(), pfPass.getText()));
        pfPass.setOnAction(e -> doInstitutionLogin(tfCode.getText(), pfPass.getText()));
        p.getChildren().addAll(hint, labeled("机构编码:", tfCode), labeled("密码:", pfPass), btn);
        return p;
    }

    private VBox buildInstitutionApplyPane() {
        VBox p = new VBox(10);
        TextField tfName = new TextField();
        tfName.setPromptText("机构名称");
        TextField tfContact = new TextField();
        tfContact.setPromptText("联系人");
        TextField tfPhone = new TextField();
        tfPhone.setPromptText("联系电话");
        TextField tfEmail = new TextField();
        tfEmail.setPromptText("联系邮箱 (审批通过后向其发送机构编号)");
        TextArea taNote = new TextArea();
        taNote.setPromptText("申请说明 (为何需要入驻, 如所属科室/用途)");
        taNote.setPrefRowCount(2);
        PasswordField tfPwd = new PasswordField();
        tfPwd.setPromptText("设置登录密码 (至少6位)");
        PasswordField tfPwd2 = new PasswordField();
        tfPwd2.setPromptText("确认密码");
        Button btn = new Button("确认提交申请");
        btn.getStyleClass().add("button-accent");
        btn.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("提交后由管理员审批; 通过后会自动将机构编号发送至您填写的邮箱, 请用「编号 + 您设置的密码」登录");
        hint.getStyleClass().add("hint");

        btn.setOnAction(e -> {
            try {
                String name = tfName.getText() == null ? "" : tfName.getText().trim();
                String contact = tfContact.getText() == null ? "" : tfContact.getText().trim();
                String phone = tfPhone.getText() == null ? "" : tfPhone.getText().trim();
                String email = tfEmail.getText() == null ? "" : tfEmail.getText().trim();
                String note = taNote.getText() == null ? "" : taNote.getText().trim();
                String pwd = tfPwd.getText() == null ? "" : tfPwd.getText();
                String pwd2 = tfPwd2.getText() == null ? "" : tfPwd2.getText();

                // 统一校验（电话/邮箱格式 + 必填项），返回错误信息或 null
                String err = validateInstitutionApply(name, contact, phone, email, pwd, pwd2);
                if (err != null) {
                    alert(Alert.AlertType.WARNING, "提示", err);
                    return;
                }
                confirmAndSubmitInstitutionRequest(name, contact, phone, email, note, pwd);
            } catch (Exception ex) {
                // 任何意外异常都不应让界面崩溃, 记录日志并友好提示
                DBUtil.logError("LoginView.buildInstitutionApplyPane.submit", ex);
                alert(Alert.AlertType.ERROR, "提交异常", "提交过程中发生异常: " + ex.getMessage() + "\n请稍后重试");
            }
        });
        p.getChildren().addAll(hint, labeled("机构名称:", tfName),
                labeled("联系人:", tfContact), labeled("联系电话:", tfPhone),
                labeled("联系邮箱:", tfEmail),
                new Label("申请说明:"), taNote,
                labeled("登录密码:", tfPwd), labeled("确认密码:", tfPwd2), btn);
        return p;
    }

    /** 机构入驻申请校验：返回错误提示文本（无错误返回 null）。电话/邮箱格式均做校验。 */
    private String validateInstitutionApply(String name, String contact, String phone,
                                           String email, String pwd, String pwd2) {
        if (name.isEmpty()) return "请填写机构名称";
        if (contact.isEmpty()) return "请填写联系人";
        if (phone.isEmpty()) return "请填写联系电话";
        if (!isValidPhone(phone)) return "联系电话格式不正确 (应为 7-15 位数字, 可含 +、-、空格、括号)";
        if (email.isEmpty()) return "请填写联系邮箱 (审批通过后向其发送机构编号)";
        if (!isValidEmail(email)) return "邮箱格式不正确, 请检查后重新填写";
        if (pwd.isEmpty() || pwd.length() < 6) return "密码至少 6 位";
        if (!pwd.equals(pwd2)) return "两次密码不一致";
        return null;
    }

    /** 电话格式：去除非数字后 7-15 位, 且整体仅含数字/+/空格/()- */
    private boolean isValidPhone(String p) {
        try {
            String digits = p.replaceAll("\\D", "");
            if (digits.length() < 7 || digits.length() > 15) return false;
            return p.matches("[0-9+\\-()\\s]+");
        } catch (Exception e) {
            return false;
        }
    }

    /** 邮箱格式：标准 用户名@域名.后缀 */
    private boolean isValidEmail(String e) {
        try {
            return e.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        } catch (Exception ex) {
            return false;
        }
    }

    /** 提交前的二次确认（提供明确的「确认」按钮, 避免误提交） */
    private void confirmAndSubmitInstitutionRequest(String name, String contact, String phone,
                                                    String email, String note, String pwd) {
        Alert cf = new Alert(Alert.AlertType.CONFIRMATION,
                "确认提交以下机构入驻申请?\n\n机构名称: " + name
                        + "\n联系人: " + contact + "\n联系电话: " + phone + "\n联系邮箱: " + email
                        + "\n\n提交后状态为「待审批」, 通过后机构编号将自动发送至上述邮箱。",
                ButtonType.OK, ButtonType.CANCEL);
        cf.setTitle("确认提交");
        cf.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                doSubmitInstitutionRequest(name, contact, phone, email, note, pwd);
            }
        });
    }

    private HBox labeled(String text, Control c) {
        HBox h = new HBox(8);
        h.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(text);
        l.setMinWidth(70);
        HBox.setHgrow(c, Priority.ALWAYS);
        c.setMaxWidth(Double.MAX_VALUE);
        h.getChildren().addAll(l, c);
        return h;
    }

    private void doInstitutionLogin(String code, String pwd) {
        if (code == null || code.trim().isEmpty() || pwd == null || pwd.isEmpty()) {
            alert(Alert.AlertType.WARNING, "提示", "请输入机构编码和密码");
            return;
        }
        if (DBUtil.loginInstitution(code, pwd)) {
            App.currentRole = "institution";
            App.showInstitution();
        } else {
            alert(Alert.AlertType.ERROR, "登录失败", "机构编码或密码错误");
        }
    }

    private void doSubmitInstitutionRequest(String name, String contact, String phone, String email, String note, String pwd) {
        if (DBUtil.submitInstitutionRequest(name, contact, phone, email, note, pwd)) {
            alert(Alert.AlertType.INFORMATION, "已提交",
                    "入驻申请已提交, 状态: 待审批。\n请等待管理员审核, 通过后系统会自动将机构编号发送至您填写的邮箱 ("
                            + email + ");\n届时请用「编号 + 您刚才设置的密码」登录。");
        } else {
            alert(Alert.AlertType.ERROR, "提交失败", "申请提交失败, 请稍后重试");
        }
    }

    public BorderPane getRoot() {
        return root;
    }
}
