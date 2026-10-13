package cn.ysmp.ai.guard;

import cn.ysmp.ai.constants.AiLimits;
import cn.ysmp.ai.constants.AiQueryIntents;
import cn.ysmp.ai.constants.AiToolQueryParamNames;
import cn.ysmp.ai.tool.AiToolDefinition;
import cn.ysmp.ai.tool.AiToolKindHints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool 查询门禁：无约束分页拒绝；大屏意图拒明细分页；澄清轮拒拉取。
 */
public final class AiToolQueryGuard {

    public static final String ERROR_CODE = "REFUSED_UNCONSTRAINED_QUERY";
    public static final String ERROR_WRONG_TOOL = "REFUSED_WRONG_TOOL_FOR_INTENT";
    public static final String ERROR_NEED_CLARIFY = "REFUSED_NEED_CLARIFY_FIRST";

    private AiToolQueryGuard() {
    }

    public record CheckResult(boolean allowed, String refusalJson, Map<String, Object> sanitizedArgs) {
        public static CheckResult ok(Map<String, Object> args) {
            return new CheckResult(true, null, args);
        }

        public static CheckResult refuse(String json) {
            return new CheckResult(false, json, null);
        }
    }

    public static CheckResult check(AiToolDefinition def, Map<String, Object> arguments) {
        return check(def, arguments, null, false);
    }

    public static CheckResult check(AiToolDefinition def, Map<String, Object> arguments, String intent) {
        return check(def, arguments, intent, false);
    }

    /**
     * @param intent              {@link AiQueryIntents}，可为 null
     * @param blockDataFetch      兜底：本轮禁止拉数（无大屏/需澄清/META）
     */
    public static CheckResult check(AiToolDefinition def,
                                    Map<String, Object> arguments,
                                    String intent,
                                    boolean blockDataFetch) {
        if (def == null) {
            return CheckResult.ok(arguments == null ? Map.of() : arguments);
        }
        Map<String, Object> args = arguments == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(arguments);

        clampPageSize(args);

        if (blockDataFetch) {
            if (AiToolKindHints.looksLikePagedList(def) || AiToolKindHints.looksLikeDashboard(def)
                    || AiQueryIntents.isDashboard(intent)) {
                String why = AiQueryIntents.isDashboard(intent)
                        ? "当前为大屏意图但无可用大屏 Tool（或需先澄清），禁止拉数硬查；请说明并 ai-clarify 改问明细/汇总。"
                        : "当前兜底策略禁止本轮拉取分页/大屏数据，请先澄清或仅说明能力。";
                return CheckResult.refuse(refusalJson(ERROR_NEED_CLARIFY, def.getToolCode(), List.of(),
                        why, "clarify_first"));
            }
        }

        if (AiQueryIntents.CLARIFY_NEEDED.equals(intent) || AiQueryIntents.META.equals(intent)) {
            if (AiToolKindHints.looksLikePagedList(def) || AiToolKindHints.looksLikeDashboard(def)) {
                return CheckResult.refuse(refusalJson(ERROR_NEED_CLARIFY, def.getToolCode(), List.of(),
                        "当前意图为 " + intent + "：本轮禁止拉取分页/大屏数据，请先澄清或仅说明能力。",
                        "clarify_first"));
            }
        }

        if (AiQueryIntents.isDashboard(intent)
                && AiToolKindHints.looksLikePagedList(def)
                && !AiToolKindHints.looksLikeDashboard(def)) {
            return CheckResult.refuse(refusalJson(ERROR_WRONG_TOOL, def.getToolCode(), List.of(),
                    "当前为大屏意图（趋势/对比/排行/分布），禁止用明细分页 Tool「"
                            + nullToEmpty(def.getToolCode()) + "」拼数。"
                            + "请改用大屏/看板/指标类 Tool；若无可用大屏 Tool，请说明并 ai-clarify。",
                    "use_dashboard_tool"));
        }

        if (AiQueryIntents.LOOKUP.equals(intent)
                && AiToolKindHints.looksLikePagedList(def)
                && !AiToolKindHints.looksLikeDetailLookup(def)
                && !hasAnyConstraint(args, Set.of())) {
            return CheckResult.refuse(refusalJson(ERROR_WRONG_TOOL, def.getToolCode(), List.of(),
                    "LOOKUP 意图请用详情/getById，或带单号等唯一约束；禁止无约束分页模糊查。",
                    "use_detail_or_filter"));
        }

        if (!looksLikePagedQuery(def)) {
            return CheckResult.ok(args);
        }

        List<String> filterFields = filterableFields(def.getParametersSchema());
        if (filterFields.isEmpty()) {
            if (!hasAnyConstraint(args, Set.of())) {
                return CheckResult.refuse(refusalJson(ERROR_CODE, def.getToolCode(), List.of(),
                        "该分页/列表 Tool 无可用筛选字段，禁止无范围全量拉取。"
                                + "若用户要汇总/大屏，请改用聚合或大屏 Tool；否则用 ai-clarify。",
                        "clarify_or_retry_with_filters"));
            }
            return CheckResult.ok(args);
        }

        if (!hasAnyConstraint(args, new LinkedHashSet<>(filterFields))) {
            return CheckResult.refuse(refusalJson(ERROR_CODE, def.getToolCode(), filterFields,
                    "禁止无筛选条件调用分页/列表 Tool（可能全表扫描）。"
                            + "请至少提供一个业务约束后再调用；用户未给出则 ai-clarify。",
                    "clarify_or_retry_with_filters"));
        }
        return CheckResult.ok(args);
    }

    static boolean looksLikePagedQuery(AiToolDefinition def) {
        return AiToolKindHints.looksLikePagedList(def)
                || (def != null && schemaHasPagingProps(def.getParametersSchema())
                && !AiToolKindHints.looksLikeDashboard(def));
    }

    @SuppressWarnings("unchecked")
    private static boolean schemaHasPagingProps(Map<String, Object> schema) {
        Map<String, Object> props = propertiesOf(schema);
        if (props.isEmpty()) {
            return false;
        }
        for (String key : props.keySet()) {
            if (AiToolQueryParamNames.isPaging(key)) {
                return true;
            }
            Object child = props.get(key);
            if (child instanceof Map<?, ?> m && m.get("properties") instanceof Map<?, ?>) {
                if (schemaHasPagingProps((Map<String, Object>) child)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static List<String> filterableFields(Map<String, Object> schema) {
        List<String> out = new ArrayList<>();
        Map<String, Object> props = propertiesOf(schema);
        for (Map.Entry<String, Object> e : props.entrySet()) {
            String key = e.getKey();
            if (AiToolQueryParamNames.isNonFilter(key)) {
                continue;
            }
            // 嵌套 query/params 对象：展开其子字段
            if (e.getValue() instanceof Map<?, ?> m
                    && "object".equals(String.valueOf(m.get("type")))
                    && m.get("properties") instanceof Map<?, ?> nested) {
                for (Object nk : nested.keySet()) {
                    String child = String.valueOf(nk);
                    if (!AiToolQueryParamNames.isNonFilter(child)) {
                        out.add(key + "." + child);
                    }
                }
                continue;
            }
            out.add(key);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Map<String, Object> schema) {
        if (schema == null) {
            return Map.of();
        }
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    /**
     * @param knownFilters 若非空，仅这些字段（或点号父级）计为约束；空则任意非分页键均可
     */
    @SuppressWarnings("unchecked")
    static boolean hasAnyConstraint(Map<String, Object> args, Set<String> knownFilters) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> e : args.entrySet()) {
            String key = e.getKey();
            if (AiToolQueryParamNames.isNonFilter(key)) {
                continue;
            }
            Object val = e.getValue();
            if (val instanceof Map<?, ?> nested) {
                Map<String, Object> child = (Map<String, Object>) nested;
                if (knownFilters.isEmpty()) {
                    if (hasAnyConstraint(child, Set.of())) {
                        return true;
                    }
                } else {
                    // 匹配 parent.child
                    Set<String> childFilters = new LinkedHashSet<>();
                    String prefix = key + ".";
                    for (String f : knownFilters) {
                        if (f.startsWith(prefix)) {
                            childFilters.add(f.substring(prefix.length()));
                        }
                    }
                    if (!childFilters.isEmpty() && hasAnyConstraint(child, childFilters)) {
                        return true;
                    }
                    if (knownFilters.contains(key) && hasMeaningfulValue(val)) {
                        return true;
                    }
                }
                continue;
            }
            if (!knownFilters.isEmpty() && !knownFilters.contains(key)
                    && knownFilters.stream().noneMatch(f -> f.startsWith(key + "."))) {
                // 未知键但仍有值：宽松视为约束（模型可能用了 schema 外字段）
                if (hasMeaningfulValue(val)) {
                    return true;
                }
                continue;
            }
            if (hasMeaningfulValue(val)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMeaningfulValue(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof String s) {
            return !s.isBlank();
        }
        if (val instanceof Collection<?> c) {
            if (c.isEmpty()) {
                return false;
            }
            for (Object o : c) {
                if (hasMeaningfulValue(o)) {
                    return true;
                }
            }
            return false;
        }
        if (val instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                return false;
            }
            for (Object o : m.values()) {
                if (hasMeaningfulValue(o)) {
                    return true;
                }
            }
            return false;
        }
        // number / boolean / 其它标量
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void clampPageSize(Map<String, Object> args) {
        if (args == null) {
            return;
        }
        for (String key : List.of("pageSize", "page_size", "size", "limit")) {
            if (!args.containsKey(key)) {
                continue;
            }
            Object v = args.get(key);
            Integer n = toInt(v);
            if (n != null && n > AiLimits.TOOL_QUERY_MAX_PAGE_SIZE) {
                args.put(key, AiLimits.TOOL_QUERY_MAX_PAGE_SIZE);
            }
        }
        for (Object v : args.values()) {
            if (v instanceof Map<?, ?> m) {
                clampPageSize((Map<String, Object>) m);
            }
        }
    }

    private static Integer toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String refusalJson(String error,
                                      String toolCode,
                                      List<String> hintFields,
                                      String message,
                                      String action) {
        StringBuilder sb = new StringBuilder(320);
        sb.append("{\"error\":\"").append(escape(error)).append("\",");
        sb.append("\"toolCode\":\"").append(escape(toolCode)).append("\",");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"action\":\"").append(escape(action)).append("\",");
        sb.append("\"hintFields\":[");
        for (int i = 0; i < hintFields.size() && i < 24; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(hintFields.get(i))).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
