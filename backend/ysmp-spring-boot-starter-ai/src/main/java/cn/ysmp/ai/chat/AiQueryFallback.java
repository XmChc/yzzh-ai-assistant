package cn.ysmp.ai.chat;

import cn.ysmp.ai.constants.AiClarifyConstants;
import cn.ysmp.ai.constants.AiQueryIntents;
import cn.ysmp.ai.guard.AiToolQueryGuard;

import java.util.List;

/**
 * 查询意图兜底策略：无大屏 Tool、连续拒调、缺槽时引导澄清，避免空转硬查。
 */
public final class AiQueryFallback {

    public static final String FALLBACK_STOP = "FALLBACK_STOP_AND_CLARIFY";

    private AiQueryFallback() {
    }

    public enum Mode {
        /** 正常按意图执行 */
        NORMAL,
        /** 大屏意图且存在大屏 Tool */
        DASHBOARD_READY,
        /** 大屏意图但无可调用大屏 Tool → 澄清兜底，禁止明细分页假扮 */
        DASHBOARD_UNAVAILABLE,
        /** 缺槽，本轮只澄清 */
        CLARIFY_ONLY,
        /** 能力询问 */
        META_ONLY
    }

    public record Plan(Mode mode, String intent, boolean hasScope, boolean dashboardToolsPresent) {
        public boolean refusePagedForDashboard() {
            return mode == Mode.DASHBOARD_READY || mode == Mode.DASHBOARD_UNAVAILABLE;
        }

        public boolean blockDataFetch() {
            return mode == Mode.CLARIFY_ONLY || mode == Mode.META_ONLY || mode == Mode.DASHBOARD_UNAVAILABLE;
        }
    }

    public static Plan resolve(AiQueryIntentDetector.Detection detection, List<String> dashboardToolCodes) {
        if (detection == null) {
            return new Plan(Mode.NORMAL, AiQueryIntents.UNKNOWN, false, false);
        }
        String intent = detection.intent() == null ? AiQueryIntents.UNKNOWN : detection.intent();
        boolean hasDash = dashboardToolCodes != null && !dashboardToolCodes.isEmpty();

        if (AiQueryIntents.META.equals(intent)) {
            return new Plan(Mode.META_ONLY, intent, detection.hasScope(), hasDash);
        }
        if (AiQueryIntents.CLARIFY_NEEDED.equals(intent)) {
            return new Plan(Mode.CLARIFY_ONLY, intent, detection.hasScope(), hasDash);
        }
        if (AiQueryIntents.isDashboard(intent)) {
            return new Plan(hasDash ? Mode.DASHBOARD_READY : Mode.DASHBOARD_UNAVAILABLE,
                    intent, detection.hasScope(), hasDash);
        }
        // UNKNOWN 且无范围：偏澄清兜底，降低误调全表风险
        if (AiQueryIntents.UNKNOWN.equals(intent) && !detection.hasScope() && !detection.clarifyReply()) {
            return new Plan(Mode.CLARIFY_ONLY, intent, false, hasDash);
        }
        return new Plan(Mode.NORMAL, intent, detection.hasScope(), hasDash);
    }

    public static String promptSection(Plan plan) {
        if (plan == null) {
            return "";
        }
        return switch (plan.mode()) {
            case DASHBOARD_READY -> "\n\n# 兜底策略：大屏可用\n"
                    + "优先「大屏推荐」Tool；若调用被拒，换其它大屏 Tool 或澄清参数，禁止改调明细分页拼数。\n";
            case DASHBOARD_UNAVAILABLE -> "\n\n# 兜底策略：无大屏 Tool（必须遵守）\n"
                    + "当前可调用集中没有大屏/看板类 Tool，无法可靠回答趋势/对比/排行/分布。\n"
                    + "- 本轮禁止调用明细分页 Tool 冒充大屏结果。\n"
                    + "- 直接向用户说明能力缺口，并用 " + AiClarifyConstants.FENCE_LANG
                    + " 引导改问：有范围的明细列表、单值汇总，或提供可用的大屏指标名称。\n"
                    + "- 不要编造图表数据。\n";
            case CLARIFY_ONLY -> "\n\n# 兜底策略：先澄清\n"
                    + "关键槽位不足或意图不明：本轮不要拉数，输出简短说明 + "
                    + AiClarifyConstants.FENCE_LANG + "（时间范围/单号/要明细还是汇总等）。\n";
            case META_ONLY -> "\n\n# 兜底策略：只谈能力\n"
                    + "不要调用业务 Tool，基于当前 Agent/Skill 说明能做什么。\n";
            case NORMAL -> {
                if (!plan.hasScope() && (AiQueryIntents.LIST.equals(plan.intent())
                        || AiQueryIntents.AGGREGATE.equals(plan.intent())
                        || AiQueryIntents.EXPORT.equals(plan.intent()))) {
                    yield "\n\n# 兜底策略：缺范围\n"
                            + "列表/汇总/导出须有范围；若用户未给，先 "
                            + AiClarifyConstants.FENCE_LANG + "，禁止无筛选分页。\n";
                }
                yield "\n\n# 兜底策略：常规\n"
                        + "Tool 被拒后按错误码修正；连续失败则停止调 Tool，改为澄清或缩小范围，禁止空转重试。\n";
            }
        };
    }

    /**
     * 门禁拒绝后的兜底附言：提示模型停止空转。
     */
    public static String enrichRefusal(String refusalJson, Plan plan, int refusalCount) {
        String base = refusalJson == null ? "{}" : refusalJson;
        if (refusalCount < 2) {
            return base;
        }
        // 连续拒绝：强制停止 Tool 循环
        String msg = "已连续多次 Tool 调用被拒。请停止再次调用 Tool，"
                + "向用户说明原因，并输出 " + AiClarifyConstants.FENCE_LANG + " 收集必要条件。"
                + (plan != null && plan.mode() == Mode.DASHBOARD_UNAVAILABLE
                ? "（当前无大屏 Tool，请引导改问明细/汇总。）" : "");
        if (base.endsWith("}")) {
            return base.substring(0, base.length() - 1)
                    + ",\"fallback\":true,\"fallbackCode\":\"" + FALLBACK_STOP + "\","
                    + "\"fallbackMessage\":\"" + escape(msg) + "\"}";
        }
        return "{\"error\":\"" + AiToolQueryGuard.ERROR_CODE + "\",\"fallback\":true,"
                + "\"fallbackCode\":\"" + FALLBACK_STOP + "\",\"fallbackMessage\":\"" + escape(msg) + "\"}";
    }

    /**
     * 若模型在兜底场景仍不产出澄清，编排层可追加的最短提示（写入最终回复前）。
     */
    public static String ensureUserFacingFallback(String content, Plan plan, int refusalCount) {
        if (plan == null) {
            return content;
        }
        boolean need = plan.mode() == Mode.DASHBOARD_UNAVAILABLE
                || plan.mode() == Mode.CLARIFY_ONLY
                || refusalCount >= 2;
        if (!need) {
            return content;
        }
        String body = content == null ? "" : content.trim();
        if (body.contains(AiClarifyConstants.FENCE_LANG) || body.contains("```" + AiClarifyConstants.FENCE_LANG)) {
            return content;
        }
        String tip = switch (plan.mode()) {
            case DASHBOARD_UNAVAILABLE -> "当前环境未配置可用的大屏/看板类工具，无法直接给出趋势/对比/排行/分布。"
                    + "请改问「有时间范围的明细或合计」，或联系管理员接入大屏接口。";
            case CLARIFY_ONLY -> "还需要补充查询条件后才能继续。";
            default -> "当前无法安全完成查询，请补充范围或更换问法后重试。";
        };
        String clarify = defaultClarifyJson(plan);
        if (body.isBlank()) {
            return tip + "\n\n```" + AiClarifyConstants.FENCE_LANG + "\n" + clarify + "\n```";
        }
        return body + "\n\n" + tip + "\n\n```" + AiClarifyConstants.FENCE_LANG + "\n" + clarify + "\n```";
    }

    private static String defaultClarifyJson(Plan plan) {
        if (plan != null && plan.mode() == Mode.DASHBOARD_UNAVAILABLE) {
            return """
                    {
                      "id": "fallback_dashboard",
                      "title": "请调整提问方式",
                      "description": "大屏类分析暂不可用，可改问有范围的明细或合计",
                      "fields": [
                        {"id":"askType","label":"想了解什么","type":"single","required":true,"options":[
                          {"value":"list","label":"有时间范围的明细列表"},
                          {"value":"sum","label":"有时间范围的合计/总数"}
                        ]},
                        {"id":"timeRange","label":"时间范围","type":"text","required":true,"placeholder":"例如：最近1个月"}
                      ]
                    }""";
        }
        return """
                {
                  "id": "fallback_scope",
                  "title": "请补充查询条件",
                  "description": "缺少时间范围或单号等关键信息，无法安全查询",
                  "fields": [
                    {"id":"timeRange","label":"时间范围","type":"text","required":false,"placeholder":"例如：最近1个月"},
                    {"id":"bizNo","label":"单号/编号","type":"text","required":false,"placeholder":"如有请填写"}
                  ]
                }""";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
