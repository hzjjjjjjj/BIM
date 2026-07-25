package com.bmi.util;

import com.bmi.util.BodyModel3D.BodyParams;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 2D 体型示意图（正面剪影）。
 *
 * 用途：当运行环境不支持 JavaFX 3D（典型如远程桌面 / 虚拟机 / 显卡驱动被降级，
 * Prism 回退到「软件渲染管线」）时，SubScene 的 3D 网格不会绘制、只剩深色背景。
 * 此时用 Canvas 2D 画出人体剪影，保证任何环境都能「看得到体型 + 做往期对比」。
 *
 * 与 BodyModel3D 共用同一套 BodyParams：胖瘦/腰腹/四肢比例、肩髋宽、身高缩放、
 * BMI 分级配色完全一致，因此 3D 与 2D 两种视图在「体型含义」上是对齐的。
 */
public class BodyModel2D {

    /** 把一条体型参数画成正面人形剪影。
     * @param g     画布上下文（已按需清空）
     * @param p     体型参数（来自 BodyModel3D.computeParams / fromRecord）
     * @param cx    人形水平中心 x（像素）
     * @param baseY 脚底基线 y（像素，画布坐标系 y 向下为正）
     * @param figH  人形总高（像素）
     */
    public static void draw(GraphicsContext g, BodyParams p, double cx, double baseY, double figH) {
        double u = figH;                                  // 以人形总高为基准单位
        // 各部位像素尺寸：基于 BodyParams 的相对比例，做适当放大让剪影更直观
        double headR      = 0.058 * u;
        double neckHW     = headR * 0.55;
        double neckLen    = 0.045 * u;
        double shoulderHW = clamp(p.shoulderW() * 1.9, 0.10, 0.46) * u;
        double hipHW      = clamp(p.hipW() * 1.9, 0.10, 0.46) * u;
        double torsoHW    = clamp(p.torsoW() * 3.2, 0.10, 0.42) * u;
        double bellyR     = clamp(p.bellyR() * 2.4, 0.10, 0.50) * u;
        double limbR      = clamp(p.limbR() * 4.5, 0.04, 0.16) * u;

        // 竖直布局：从脚底 baseY 向上生长
        double legLen     = 0.42 * u;
        double hipY       = baseY - legLen;
        double torsoBottom = hipY;
        double torsoTop   = hipY - 0.34 * u;
        double shoulderY  = torsoTop + 0.02 * u;
        double headCY     = shoulderY - neckLen - headR;

        Color fill = p.color();
        Color outline = darker(fill, 0.6);
        g.setFill(fill);
        g.setStroke(outline);
        g.setLineWidth(Math.max(1.2, u * 0.012));

        // 腿（两条圆角矩形）
        for (double s : new double[]{-1, 1}) {
            double lx = cx + s * hipHW * 0.5 - limbR;
            fillRoundRect(g, lx, torsoBottom, limbR * 2, legLen, limbR);
        }
        // 盆骨
        fillEllipse(g, cx, hipY, hipHW, hipHW * 0.7);
        // 手臂（自肩垂下，略外撇）
        double armLen = 0.30 * u;
        for (double s : new double[]{-1, 1}) {
            double ax = cx + s * (shoulderHW + limbR * 0.7) - limbR * 0.9;
            fillRoundRect(g, ax, shoulderY - armLen * 0.1, limbR * 1.8, armLen, limbR * 0.9);
        }
        // 躯干（肩→髋梯形，带腰腹前凸）
        drawTorso(g, cx, shoulderY, shoulderHW, torsoBottom, hipHW, torsoHW, bellyR);
        // 颈
        fillRoundRect(g, cx - neckHW, shoulderY - neckLen, neckHW * 2, neckLen + 0.01 * u, neckHW * 0.6);
        // 头
        fillEllipse(g, cx, headCY, headR, headR);
    }

    /** 躯干：肩宽梯形 + 腰部内收 + 腰腹前凸（椭圆叠加） */
    private static void drawTorso(GraphicsContext g, double cx,
                                   double shoulderY, double shoulderHW,
                                   double bottomY, double hipHW,
                                   double torsoHW, double bellyR) {
        double waistY = (shoulderY + bottomY) / 2.0;
        double waistHW = Math.min(shoulderHW, hipHW) * 0.82;   // 腰略收
        g.beginPath();
        g.moveTo(cx - shoulderHW, shoulderY);
        g.lineTo(cx + shoulderHW, shoulderY);
        g.lineTo(cx + waistHW, waistY);                        // 右侧收到腰
        g.lineTo(cx + hipHW, bottomY);                         // 再展到髋
        g.lineTo(cx - hipHW, bottomY);
        g.lineTo(cx - waistHW, waistY);
        g.lineTo(cx - shoulderHW, shoulderY);
        g.closePath();
        g.fill();
        g.stroke();
        // 腰腹前凸：在腰线前方叠加一个椭圆，体脂越高 bellyR 越大、越突出
        if (bellyR > waistHW) {
            fillEllipse(g, cx, waistY + (bottomY - waistY) * 0.35, bellyR, bellyR * 0.95);
        }
    }

    // ---------------- 小工具 ----------------
    private static void fillRoundRect(GraphicsContext g, double x, double y, double w, double h, double r) {
        g.fillRoundRect(x, y, w, h, r, r);
        g.strokeRoundRect(x, y, w, h, r, r);
    }
    private static void fillEllipse(GraphicsContext g, double cx, double cy, double rx, double ry) {
        g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
        g.strokeOval(cx - rx, cy - ry, rx * 2, ry * 2);
    }
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static Color darker(Color c, double f) {
        return Color.color(c.getRed() * f, c.getGreen() * f, c.getBlue() * f, 1.0);
    }
}
