package cn.ysmp.ai.constants;

import java.util.Locale;
import java.util.Set;

/**
 * 聚合/汇总类 Tool 识别标记（code / name / path / description）。
 * <p>宿主应提供带业务权限码的汇总 API，并在 OpenAPI 描述中带上这些关键词以便选型。
 */
public final class AiAggregateToolMarkers {

    private AiAggregateToolMarkers() {
    }

    public static final Set<String> KEYWORDS = Set.of(
            "sum", "total", "stat", "stats", "aggregate", "aggregation", "summary", "count",
            "汇总", "合计", "统计", "总数", "总额", "总量", "求和", "聚合"
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
