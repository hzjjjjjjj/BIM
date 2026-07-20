import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标注册表 — 集中管理所有可绘制健康属性。
 *
 * 顺序即「图表调色板顺序」：与 style.css 中 .default-color0~10 的配色一一对应，
 * 因此无论用户勾选哪些指标，系列颜色都与指标本身固定绑定（系列按本列表固定顺序添加）。
 *
 * 三档分组：
 *   必备 REQUIRED   —— 体重、腰围（基础录入）
 *   计算 COMPUTED   —— BMI、健康评分（由必备推导）
 *   仪器 INSTRUMENT —— 体脂率、水分率、肌肉率、内脏脂肪、骨肌量、蛋白质率、皮下脂肪率
 */
public class MetricRegistry {

    private static final Color C0  = Color.rgb(45, 140, 160);  // 青
    private static final Color C1  = Color.rgb(181, 131, 90);  // 棕
    private static final Color C2  = Color.rgb(74, 144, 217);  // 蓝
    private static final Color C3  = Color.rgb(60, 180, 110);  // 绿
    private static final Color C4  = Color.rgb(255, 150, 70);  // 橙
    private static final Color C5  = Color.rgb(43, 185, 168);  // 青绿
    private static final Color C6  = Color.rgb(142, 124, 224); // 紫
    private static final Color C7  = Color.rgb(224, 101, 101); // 红
    private static final Color C8  = Color.rgb(232, 178, 58);  // 黄
    private static final Color C9  = Color.rgb(224, 107, 156); // 粉
    private static final Color C10 = Color.rgb(199, 125, 255); // 浅紫

    public static final List<Metric> ALL = new ArrayList<>();

    static {
        // ===== 必备 =====
        reg(new Metric("weight", "体重", "kg", C0, Metric.Tier.REQUIRED, 45.0, 80.0,
                r -> Metric.num(r, "weight")));
        reg(new Metric("waist", "腰围", "cm", C1, Metric.Tier.REQUIRED, 60.0, 90.0,
                r -> Metric.num(r, "waist")));

        // ===== 计算 =====
        reg(new Metric("bmi", "BMI", "", C2, Metric.Tier.COMPUTED, 18.5, 24.0,
                r -> Metric.num(r, "bmi")));
        reg(new Metric("body_score", "健康评分", "分", C3, Metric.Tier.COMPUTED, 60.0, 100.0,
                r -> Metric.num(r, "body_score")));

        // ===== 仪器 =====
        reg(new Metric("body_fat", "体脂率", "%", C4, Metric.Tier.INSTRUMENT, 12.0, 25.0,
                r -> Metric.num(r, "body_fat")));
        reg(new Metric("water_rate", "水分率", "%", C5, Metric.Tier.INSTRUMENT, 50.0, 65.0,
                r -> Metric.num(r, "water_rate")));
        reg(new Metric("muscle_rate", "肌肉率", "%", C6, Metric.Tier.INSTRUMENT, 30.0, 45.0,
                r -> Metric.num(r, "muscle_rate")));
        reg(new Metric("visceral_fat", "内脏脂肪", "级", C7, Metric.Tier.INSTRUMENT, 1.0, 9.0,
                r -> Metric.num(r, "visceral_fat")));
        reg(new Metric("bone_muscle", "骨肌量", "kg", C8, Metric.Tier.INSTRUMENT, 2.0, 4.0,
                r -> Metric.num(r, "bone_muscle")));
        reg(new Metric("protein_rate", "蛋白质率", "%", C9, Metric.Tier.INSTRUMENT, 14.0, 20.0,
                r -> Metric.num(r, "protein_rate")));
        reg(new Metric("subcutaneous_fat", "皮下脂肪率", "%", C10, Metric.Tier.INSTRUMENT, 10.0, 25.0,
                r -> Metric.num(r, "subcutaneous_fat")));
    }

    private static void reg(Metric m) {
        ALL.add(m);
    }

    public static Metric byKey(String key) {
        for (Metric m : ALL) if (m.key.equals(key)) return m;
        return null;
    }

    public static List<Metric> byTier(Metric.Tier tier) {
        List<Metric> list = new ArrayList<>();
        for (Metric m : ALL) if (m.tier == tier) list.add(m);
        return list;
    }

    /** Color 转 #RRGGBB（供 CSS / 内联样式使用） */
    public static String hex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }
}
