package cn.ysmp.ai.constants;

import java.util.Locale;
import java.util.Set;

/**
 * 大屏/看板类 Tool 识别标记（code / name / path / description）。
 */
public final class AiDashboardToolMarkers {

    private AiDashboardToolMarkers() {
    }

    public static final Set<String> KEYWORDS = Set.of(
            "大屏", "看板", "仪表盘", "指标", "屏",
            "dashboard", "screen", "board", "widget",
            "chart", "stat", "metric", "kpi", "bi",
            "trend", "rank", "topn", "compare", "distribution",
            "趋势", "排行", "对比", "分布", "占比", "同比", "环比"
    );

    public static boolean matchesText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
