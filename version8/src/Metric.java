import javafx.scene.paint.Color;

import java.util.Map;
import java.util.function.Function;

/**
 * 健康指标抽象模型 — 图表设计框架的「元数据层」。
 *
 * 每个健康属性都被描述为一个 Metric：字段 key、中文显示名、单位、图表颜色、
 * 所属档位（必备/计算/仪器）、正常参考区间，以及从一条健康记录 Map 中取值的 getter。
 * 新增一个可绘制的属性，只需在 MetricRegistry 里注册一条，图表/统计/明细表自动支持。
 */
public class Metric {

    /** 三档数据模型（与 version8 的属性分级一致） */
    public enum Tier {
        /** 必备：注册时填写 / 基础录入（体重、腰围） */
        REQUIRED,
        /** 计算：由必备属性推导（BMI、健康评分） */
        COMPUTED,
        /** 仪器：需测量设备（体脂率、水分率、肌肉率、内脏脂肪、骨肌量、蛋白质率、皮下脂肪率） */
        INSTRUMENT
    }

    public final String key;
    public final String label;
    public final String unit;
    public final Color color;
    public final Tier tier;
    public final Double normalMin;
    public final Double normalMax;
    private final Function<Map<String, Object>, Double> getter;

    public Metric(String key, String label, String unit, Color color, Tier tier,
                  Double normalMin, Double normalMax,
                  Function<Map<String, Object>, Double> getter) {
        this.key = key;
        this.label = label;
        this.unit = unit;
        this.color = color;
        this.tier = tier;
        this.normalMin = normalMin;
        this.normalMax = normalMax;
        this.getter = getter;
    }

    /** 从一条健康记录中取其数值 */
    public double value(Map<String, Object> record) {
        return getter == null ? 0.0 : getter.apply(record);
    }

    /** 数值是否落在正常参考区间内 */
    public boolean inRange(double v) {
        if (normalMin == null || normalMax == null) return true;
        return v >= normalMin && v <= normalMax;
    }

    /** 安全取数（Map 中数值转 double，缺失/非数回 0） */
    public static double num(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }
}
