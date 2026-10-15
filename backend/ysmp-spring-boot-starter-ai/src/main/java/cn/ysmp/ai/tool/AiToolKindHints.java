package cn.ysmp.ai.tool;

import cn.ysmp.ai.constants.AiAggregateToolMarkers;
import cn.ysmp.ai.constants.AiDashboardToolMarkers;
import cn.ysmp.ai.constants.AiToolQueryParamNames;
import cn.ysmp.ai.constants.AiWriteToolMarkers;

import java.util.Locale;
import java.util.Map;

/**
 * Tool 形态粗判：大屏 / 分页列表 / 详情等（供意图门禁与排序）。
 */
public final class AiToolKindHints {

    private AiToolKindHints() {
    }

    public static boolean looksLikeDashboard(AiToolDefinition def) {
        if (def == null) {
            return false;
        }
        return AiDashboardToolMarkers.matchesText(def.getToolCode())
                || AiDashboardToolMarkers.matchesText(def.getName())
                || AiDashboardToolMarkers.matchesText(def.getPath())
                || AiDashboardToolMarkers.matchesText(def.getDescription())
                || AiDashboardToolMarkers.matchesText(def.getModule());
    }

    public static boolean looksLikePagedList(AiToolDefinition def) {
        if (def == null) {
            return false;
        }
        if (looksLikeDashboard(def)) {
            // 大屏接口也可能带 page，不算「明细分页」
            return false;
        }
        String code = nz(def.getToolCode()).toLowerCase(Locale.ROOT);
        String path = nz(def.getPath()).toLowerCase(Locale.ROOT);
        String name = nz(def.getName()).toLowerCase(Locale.ROOT);
        if (code.contains("page") || code.contains("list")
                || path.contains("/page") || path.contains("/list")
                || name.contains("分页") || name.contains("列表")) {
            return true;
        }
        return schemaHasPaging(def.getParametersSchema());
    }

    public static boolean looksLikeDetailLookup(AiToolDefinition def) {
        if (def == null) {
            return false;
        }
        String blob = (nz(def.getToolCode()) + " " + nz(def.getName()) + " " + nz(def.getPath())
                + " " + nz(def.getDescription())).toLowerCase(Locale.ROOT);
        return blob.contains("detail") || blob.contains("getbyid") || blob.contains("get-by")
                || blob.contains("详情") || blob.contains("/get") || blob.contains("info");
    }

    /** 聚合/汇总接口（AGGREGATE 意图优先） */
    public static boolean looksLikeAggregate(AiToolDefinition def) {
        if (def == null || looksLikePagedList(def)) {
            return false;
        }
        return AiAggregateToolMarkers.matchesText(def.getToolCode())
                || AiAggregateToolMarkers.matchesText(def.getName())
                || AiAggregateToolMarkers.matchesText(def.getPath())
                || AiAggregateToolMarkers.matchesText(def.getDescription())
                || AiAggregateToolMarkers.matchesText(def.getModule());
    }

    /** 写操作（须用户确认后执行） */
    public static boolean looksLikeWrite(AiToolDefinition def) {
        if (def == null) {
            return false;
        }
        return AiWriteToolMarkers.matchesText(def.getToolCode())
                || AiWriteToolMarkers.matchesText(def.getName())
                || AiWriteToolMarkers.matchesText(def.getPath())
                || AiWriteToolMarkers.matchesText(def.getDescription());
    }

    @SuppressWarnings("unchecked")
    private static boolean schemaHasPaging(Map<String, Object> schema) {
        if (schema == null) {
            return false;
        }
        Object props = schema.get("properties");
        if (!(props instanceof Map<?, ?> map)) {
            return false;
        }
        for (Object key : map.keySet()) {
            if (AiToolQueryParamNames.isPaging(String.valueOf(key))) {
                return true;
            }
            Object child = map.get(key);
            if (child instanceof Map<?, ?> m && m.get("properties") instanceof Map<?, ?>) {
                if (schemaHasPaging((Map<String, Object>) child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
