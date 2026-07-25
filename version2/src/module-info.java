/**
 * 模块描述符。
 *
 * 本项目依赖 JavaFX 17（独立 jar，非 JDK 内置），需在模块路径（module path）上加载；
 * 其余第三方库（PostgreSQL 驱动、webcam-capture、bridj、slf4j）以自动模块（automatic module）形式提供。
 */
module com.bmi.app {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;   // 3D 体型渲染: Sphere/Cylinder/SubScene/PerspectiveCamera/PhongMaterial

    // JDK 标准库
    requires java.sql;      // DBUtil 使用 JDBC
    requires java.desktop;  // ImageUtil / DietPanel 使用 java.awt.image.BufferedImage

    // 第三方（自动模块，名称由文件名推导）
    requires webcam.capture;      // com.github.sarxos.webcam.Webcam
    requires org.postgresql.jdbc; // org.postgresql.Driver（运行时加载）
    requires org.apache.poi.poi;     // org.apache.poi（Excel 读取）
    requires org.apache.poi.ooxml;   // org.apache.poi.xssf（.xlsx 解析）

    exports com.bmi;
    exports com.bmi.ui;
    exports com.bmi.ui.user;
    exports com.bmi.ui.admin;
    exports com.bmi.db;
    exports com.bmi.util;
}
