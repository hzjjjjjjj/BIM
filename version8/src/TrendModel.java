import java.util.*;
import java.util.Map;

/**
 * 趋势算法模型 — 历史趋势图表的「算法层」。
 *
 * 对选中的一组指标，基于正序健康记录计算：
 *   - 统计摘要：首值 / 末值 / 均值 / 变化量 / 变化率
 *   - 趋势方向：最小二乘线性回归斜率 → 上升 / 下降 / 平稳（相对阈值，跨指标量级通用）
 *   - 预测：未来 N 天线性外推值（数据 < 3 点返回 NaN）
 *
 * 线性回归 x 取「距首条记录的天数」，y 取指标值。
 */
public class TrendModel {

    public static class SeriesStat {
        public final Metric metric;
        public final List<Date> dates;
        public final List<Double> values;
        public final double first;
        public final double last;
        public final double mean;
        public final double delta;
        public final double deltaPct;
        public final String direction;   // 上升 / 下降 / 平稳
        public final double slope;       // 每日变化斜率
        public final double predict;     // 未来 N 天预测（NaN 表示不足）

        SeriesStat(Metric metric, List<Date> dates, List<Double> values,
                   double first, double last, double mean, double delta, double deltaPct,
                   String direction, double slope, double predict) {
            this.metric = metric;
            this.dates = dates;
            this.values = values;
            this.first = first;
            this.last = last;
            this.mean = mean;
            this.delta = delta;
            this.deltaPct = deltaPct;
            this.direction = direction;
            this.slope = slope;
            this.predict = predict;
        }
    }

    /** 分析一组指标；rawRecords 可为任意顺序，内部按日期升序整理 */
    public static List<SeriesStat> analyze(List<Map<String, Object>> rawRecords,
                                           List<Metric> metrics, int futureDays) {
        List<Map<String, Object>> recs = new ArrayList<>(rawRecords);
        recs.sort(Comparator.comparing(r -> (Date) r.get("record_date")));

        List<SeriesStat> out = new ArrayList<>();
        for (Metric m : metrics) {
            List<Date> dates = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            for (Map<String, Object> r : recs) {
                dates.add((Date) r.get("record_date"));
                values.add(m.value(r));
            }
            int n = values.size();
            if (n == 0) continue;

            double first = values.get(0);
            double last = values.get(n - 1);
            double sum = 0;
            for (double v : values) sum += v;
            double mean = sum / n;
            double delta = last - first;
            double deltaPct = first != 0 ? delta / Math.abs(first) * 100.0 : 0.0;

            double[] fit = linearFit(dates, values);
            double slope = fit[0];
            long baseTime = dates.get(0).getTime();
            double lastX = (dates.get(n - 1).getTime() - baseTime) / 86400000.0;
            double totalChange = slope * Math.max(lastX, 1e-9);
            double relChange = Math.abs(totalChange) / (Math.abs(last) < 1e-9 ? 1.0 : Math.abs(last));
            String direction;
            if (n < 3 || relChange < 0.02) direction = "平稳";
            else direction = slope > 0 ? "上升" : "下降";

            double predict = Double.NaN;
            if (n >= 3) {
                predict = slope * (lastX + futureDays) + fit[1];
            }

            out.add(new SeriesStat(m, dates, values, first, last, mean, delta, deltaPct,
                    direction, slope, predict));
        }
        return out;
    }

    /** 最小二乘拟合，返回 [slope, intercept]；天数作 x */
    private static double[] linearFit(List<Date> dates, List<Double> ys) {
        int n = ys.size();
        if (n < 2) return new double[]{0.0, n > 0 ? ys.get(0) : 0.0};
        long baseTime = dates.get(0).getTime();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = (dates.get(i).getTime() - baseTime) / 86400000.0;
            double y = ys.get(i);
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-12) return new double[]{0.0, sumY / n};
        double k = (n * sumXY - sumX * sumY) / denom;
        double b = (sumY - k * sumX) / n;
        return new double[]{k, b};
    }
}
