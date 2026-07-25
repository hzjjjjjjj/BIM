package com.bmi.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;

import java.util.Map;

/**
 * 3D 风格化人体模型辅助类（纯计算 + 图元拼装，不依赖任何外部模型文件/库）。
 *
 * 思路：把一条健康记录（身高/体重/腰围/体脂率/肌肉率/性别）映射为一组「体型参数」BodyParams，
 * 再用 JavaFX 自带 3D 图元（Sphere/Cylinder）按比例拼出一个拟人模型。
 * 体脂/肌肉等仪器指标可能为 NULL，computeParams 会自动降级估算，保证模型永远能建出来。
 *
 * 该模型为「示意」性质（非医学精确人体扫描），重点在于：随数据变化、可跨期对比。
 */
public class BodyModel3D {

    /** 体型参数（各尺寸单位：模型基准坐标；最终整体再按身高缩放） */
    public record BodyParams(
            double heightScale,  // 整体缩放（体现高矮）
            double shoulderW,    // 肩半宽
            double hipW,         // 髋半宽
            double torsoW,       // 躯干（胸）半宽
            double torsoD,       // 躯干前后厚度
            double bellyR,       // 腰腹半径（体脂越高越大）
            double limbR,        // 四肢半径（肌肉越高越粗）
            double headR,        // 头部半径
            Color color          // 配色（按 BMI/体脂分级）
    ) {}

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 从一条健康记录（Map）提取指标并计算体型参数；gender/heightCm 来自用户档案 */
    public static BodyParams fromRecord(Map<String, Object> rec, String gender, double heightCm) {
        double weight = num(rec.get("weight"));
        Double waist = (rec.get("waist") instanceof Number) ? ((Number) rec.get("waist")).doubleValue() : null;
        Double bodyFat = (rec.get("body_fat") instanceof Number) ? ((Number) rec.get("body_fat")).doubleValue() : null;
        Double muscleRate = (rec.get("muscle_rate") instanceof Number) ? ((Number) rec.get("muscle_rate")).doubleValue() : null;
        return computeParams(gender, heightCm, weight, waist, bodyFat, muscleRate);
    }

    private static double num(Object o) {
        return (o instanceof Number) ? ((Number) o).doubleValue() : 0.0;
    }

    /**
     * 核心：把生理指标映射为体型参数。
     * 任何缺失/异常指标都会降级到合理估算，且所有尺寸都做了 clamp，避免极端值把模型拉爆。
     */
    public static BodyParams computeParams(String gender, double heightCm,
                                           double weight, Double waist, Double bodyFat, Double muscleRate) {
        boolean male = gender == null || "男".equals(gender);
        double h = (heightCm > 0) ? heightCm : 170;
        double w = (weight > 0) ? weight : 65;
        double hM = h / 100.0;
        double bmi = w / (hM * hM);
        if (!Double.isFinite(bmi) || bmi <= 0) bmi = 22;

        // 腰围缺失 → 由 BMI 估算
        double wa = (waist != null && waist > 0) ? waist : clamp(60 + (bmi - 18.5) * 3.0, 50, 130);
        // 体脂率缺失 → BMI + 性别 + 年龄(缺省30) 估算
        if (bodyFat == null || bodyFat <= 0) {
            int age = 30;
            bodyFat = male ? (bmi * 1.2 + age * 0.23 - 16.2) : (bmi * 1.39 + age * 0.16 - 9.0);
        }
        bodyFat = clamp(bodyFat, 5, 50);
        // 肌肉率缺失 → 由体脂反推
        if (muscleRate == null || muscleRate <= 0) {
            muscleRate = clamp(48 - bodyFat * 0.5, 22, 55);
        }
        muscleRate = clamp(muscleRate, 20, 55);

        // 胖瘦因子 f：BMI 18→0, BMI 30→1.4
        double f = clamp((bmi - 18.0) / 12.0, 0, 1.4);
        // 体脂/肌肉对围度的微调
        double fatAdj = (bodyFat - 18.0) / 40.0;        // 体脂越高，躯干/腹部越宽
        double musAdj = (muscleRate - 35.0) / 50.0;     // 肌肉越高，肩/四肢越粗

        double torsoW = clamp(0.16 + 0.10 * f + 0.03 * fatAdj + 0.02 * musAdj, 0.10, 0.42);
        double torsoD = clamp(torsoW * (0.70 + 0.15 * f), 0.08, 0.38);
        double bellyR = clamp(0.14 + 0.12 * f + 0.05 * fatAdj, 0.10, 0.46);
        double limbR  = clamp(0.05 + 0.03 * f + 0.03 * musAdj + 0.015 * fatAdj, 0.04, 0.16);
        double shoulderW = clamp((male ? 0.22 : 0.18) + 0.06 * f + 0.04 * musAdj, 0.14, 0.46);
        double hipW = clamp((male ? 0.18 : 0.22) + 0.06 * f + 0.02 * fatAdj, 0.14, 0.46);
        double headR = 0.15;
        double heightScale = clamp(h / 170.0, 0.8, 2.0);

        return new BodyParams(heightScale, shoulderW, hipW, torsoW, torsoD, bellyR, limbR, headR, colorByBmi(bmi));
    }

    private static Color colorByBmi(double bmi) {
        if (bmi < 18.5) return Color.web("#4FC3F7");   // 偏瘦 - 蓝
        if (bmi < 24)   return Color.web("#66BB6A");   // 正常 - 绿
        if (bmi < 28)   return Color.web("#FFA726");   // 偏胖 - 橙
        return Color.web("#EF5350");                    // 肥胖 - 红
    }

    /** 两期参数线性插值（形变滑块用）。t∈[0,1]：0=完全 A，1=完全 B */
    public static BodyParams lerpParams(BodyParams a, BodyParams b, double t) {
        double k = clamp(t, 0, 1);
        return new BodyParams(
                lerp(a.heightScale, b.heightScale, k),
                lerp(a.shoulderW, b.shoulderW, k),
                lerp(a.hipW, b.hipW, k),
                lerp(a.torsoW, b.torsoW, k),
                lerp(a.torsoD, b.torsoD, k),
                lerp(a.bellyR, b.bellyR, k),
                lerp(a.limbR, b.limbR, k),
                lerp(a.headR, b.headR, k),
                lerpColor(a.color, b.color, k)
        );
    }

    private static double lerp(double a, double b, double k) { return a + (b - a) * k; }

    private static Color lerpColor(Color a, Color b, double k) {
        return new Color(
                lerp(a.getRed(), b.getRed(), k),
                lerp(a.getGreen(), b.getGreen(), k),
                lerp(a.getBlue(), b.getBlue(), k),
                1.0);
    }

    /** 用图元拼出 3D 人体，返回以脚底为原点(y=0)、整体向上生长的 Group */
    public static Group buildBody(BodyParams p) {
        Group g = new Group();
        PhongMaterial mat = new PhongMaterial(p.color);
        mat.setSpecularColor(Color.web("#eeeeee"));
        PhongMaterial limbMat = new PhongMaterial(p.color);
        limbMat.setSpecularColor(Color.web("#dddddd"));

        // 竖直基准坐标（模型单位）
        double legLen = 0.9, legY = legLen / 2.0;          // 腿: 0 → 0.9
        double pelvisY = 0.9;
        double torsoBottom = 0.9, torsoTop = 1.55, torsoCY = (torsoBottom + torsoTop) / 2, torsoH = torsoTop - torsoBottom;
        double shoulderY = 1.5;
        double neckLen = 0.13, neckCY = torsoTop + neckLen / 2;
        double headCY = torsoTop + neckLen + p.headR;

        // 地面圆盘（脚底阴影，便于辨认方向）
        Cylinder floor = new Cylinder(0.7, 0.02);
        floor.setMaterial(new PhongMaterial(Color.web("#37474F")));
        floor.setTranslateY(0.01);
        g.getChildren().add(floor);

        // 腿（两条）
        for (double sx : new double[]{-1, 1}) {
            Cylinder leg = new Cylinder(p.limbR * 1.15, legLen);
            leg.setMaterial(limbMat);
            leg.setTranslateX(sx * p.hipW * 0.5);
            leg.setTranslateY(legY);
            g.getChildren().add(leg);
        }

        // 盆骨
        Sphere pelvis = new Sphere(1);
        pelvis.setScaleX(p.hipW * 1.05); pelvis.setScaleY(0.18); pelvis.setScaleZ(p.hipW * 0.8);
        pelvis.setTranslateY(pelvisY);
        pelvis.setMaterial(mat);
        g.getChildren().add(pelvis);

        // 躯干（胸/上腹）椭球
        Sphere torso = new Sphere(1);
        torso.setScaleX(p.torsoW); torso.setScaleY(torsoH / 2.0); torso.setScaleZ(p.torsoD);
        torso.setTranslateY(torsoCY);
        torso.setMaterial(mat);
        g.getChildren().add(torso);

        // 腰腹（体脂越高越向前突出）
        Sphere belly = new Sphere(1);
        belly.setScaleX(p.bellyR * 0.95); belly.setScaleY(p.bellyR * 0.95); belly.setScaleZ(p.bellyR * 1.25);
        belly.setTranslateY(pelvisY + 0.18);
        belly.setTranslateZ(0.04);
        belly.setMaterial(mat);
        g.getChildren().add(belly);

        // 肩
        Sphere shoulder = new Sphere(1);
        shoulder.setScaleX(p.shoulderW); shoulder.setScaleY(0.13); shoulder.setScaleZ(p.shoulderW * 0.6);
        shoulder.setTranslateY(shoulderY);
        shoulder.setMaterial(mat);
        g.getChildren().add(shoulder);

        // 手臂（两条，自肩垂下）
        double armLen = 0.6, armCY = shoulderY - armLen / 2.0;
        for (double sx : new double[]{-1, 1}) {
            Cylinder arm = new Cylinder(p.limbR * 0.9, armLen);
            arm.setMaterial(limbMat);
            arm.setTranslateX(sx * (p.shoulderW * 0.95));
            arm.setTranslateY(armCY);
            g.getChildren().add(arm);
        }

        // 颈
        Cylinder neck = new Cylinder(p.headR * 0.42, neckLen);
        neck.setMaterial(mat);
        neck.setTranslateY(neckCY);
        g.getChildren().add(neck);

        // 头
        Sphere head = new Sphere(p.headR);
        head.setMaterial(mat);
        head.setTranslateY(headCY);
        g.getChildren().add(head);

        // 整体按身高缩放
        g.setScaleX(p.heightScale); g.setScaleY(p.heightScale); g.setScaleZ(p.heightScale);
        return g;
    }
}
