package cn.ysmp.ai.chat;

import cn.ysmp.ai.constants.AiClarifyConstants;
import cn.ysmp.ai.constants.AiQueryIntents;
import cn.ysmp.ai.constants.AiToolTypes;
import cn.ysmp.ai.guard.AiToolQueryGuard;
import cn.ysmp.ai.tool.AiToolDefinition;
import cn.ysmp.ai.tool.AiToolKindHints;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 查询兜底：分不清场景 → 澄清；有场景无合适 Tool → 可降级则澄清改问；
 * 连兜底都没有 → 明确拒绝并说明原因。
 */
public final class AiQueryFallback {

    public static final String FALLBACK_STOP = "FALLBACK_STOP_AND_CLARIFY";
    public static final String REFUSE_CODE = "REFUSED_NO_SUITABLE_TOOL_OR_FALLBACK";

    private AiQueryFallback() {
    }

    public enum Mode {
        /** 意图清晰且有合适 Tool */
        NORMAL,
        /** 大屏意图且有大屏 Tool */
        DASHBOARD_READY,
        /** 场景不清 / 缺槽 → 澄清兜底 */
        CLARIFY_ONLY,
        /** 场景清晰但 Tool 不匹配，仍可澄清改问（降级兜底） */
        DEGRADE_CLARIFY,
        /** 无合适 Tool 且无可用兜底 → 直接拒绝并说明原因 */
        REFUSE,
        /** 能力询问 */
        META_ONLY
    }

    public record ToolFitness(
            int total,
            int dashboard,
            int pagedList,
            int detail,
            int exportLike,
            int actionLike,
            int aggregateLike
    ) {
        public boolean isEmpty() {
            return total <= 0;
        }
    }

    public record Plan(
            Mode mode,
            String intent,
            boolean hasScope,
            boolean hasSuitableTools,
            boolean canFallback,
            String refuseReason,
            List<String> refuseDetails
    ) {
        public boolean blockDataFetch() {
            return mode == Mode.CLARIFY_ONLY
                    || mode == Mode.META_ONLY
                    || mode == Mode.DEGRADE_CLARIFY
                    || mode == Mode.REFUSE;
        }

        public boolean skipLlm() {
            return mode == Mode.REFUSE;
        }

        public boolean forceNoTools() {
            return mode == Mode.REFUSE || mode == Mode.META_ONLY
                    || mode == Mode.CLARIFY_ONLY || mode == Mode.DEGRADE_CLARIFY;
        }
    }

    public static ToolFitness assessTools(List<AiToolDefinition> tools) {
        int total = 0, dash = 0, page = 0, detail = 0, exp = 0, act = 0, agg = 0;
        if (tools != null) {
            for (AiToolDefinition t : tools) {
                if (t == null || !t.isAvailable()) {
                    continue;
                }
                total++;
                if (AiToolKindHints.looksLikeDashboard(t)) {
                    dash++;
                }
                if (AiToolKindHints.looksLikePagedList(t)) {
                    page++;
                }
                if (AiToolKindHints.looksLikeDetailLookup(t)) {
                    detail++;
                }
                String blob = blob(t);
                if (containsAny(blob, "export", "download", "导出", "下载")) {
                    exp++;
                }
                if (AiToolKindHints.looksLikeWrite(t)) {
                    act++;
                }
                if (AiToolKindHints.looksLikeAggregate(t) || AiToolTypes.GOVERNED.equals(t.getToolType())) {
                    agg++;
                }
            }
        }
        return new ToolFitness(total, dash, page, detail, exp, act, agg);
    }

    public static Plan resolve(AiQueryIntentDetector.Detection detection, List<AiToolDefinition> tools) {
        ToolFitness fit = assessTools(tools);
        if (detection == null) {
            return refuse(AiQueryIntents.UNKNOWN, false, fit,
                    "无法识别问题场景，且当前没有可执行的查询能力。",
                    List.of("意图未判定", "可调用 Tool 数=" + fit.total()));
        }

        String intent = detection.intent() == null ? AiQueryIntents.UNKNOWN : detection.intent();
        boolean hasScope = detection.hasScope();
        boolean clarifyReply = detection.clarifyReply();

        if (AiQueryIntents.META.equals(intent)) {
            return new Plan(Mode.META_ONLY, intent, hasScope, true, true, null, List.of());
        }

        // —— 分不清 / 缺槽：澄清兜底 ——
        if (AiQueryIntents.CLARIFY_NEEDED.equals(intent)
                || (AiQueryIntents.UNKNOWN.equals(intent) && !hasScope && !clarifyReply)) {
            if (fit.isEmpty()) {
                return refuse(intent, hasScope, fit,
                        "问题场景不明确，且当前 Agent 下没有可用 Tool，无法继续查询。",
                        List.of("意图=" + intent, "可调用 Tool 数=0", "建议：先配置 Skill/Tool 或换有能力的 Agent"));
            }
            return new Plan(Mode.CLARIFY_ONLY, intent, hasScope, fit.total() > 0, true, null, List.of());
        }

        boolean suitable = hasSuitableTools(intent, fit);

        // —— 大屏 ——
        if (AiQueryIntents.isDashboard(intent)) {
            if (fit.dashboard() > 0) {
                return new Plan(Mode.DASHBOARD_READY, intent, hasScope, true, true, null, List.of());
            }
            // 无大屏：若有列表/汇总类可引导改问 → 降级澄清；否则拒绝
            if (fit.pagedList() > 0 || fit.aggregateLike() > 0 || fit.total() > 0) {
                return new Plan(Mode.DEGRADE_CLARIFY, intent, hasScope, false, true,
                        null, List.of("大屏 Tool=0", "可降级引导改问明细/合计"));
            }
            return refuse(intent, hasScope, fit,
                    "这是趋势/对比/排行/分布类问题，但当前没有大屏/看板类 Tool，也没有可改问的列表或汇总类 Tool。",
                    details(intent, fit, "缺少大屏 Tool", "无可用降级 Tool"));
        }

        // —— 有明确场景但无合适 Tool ——
        if (!suitable) {
            if (canDegradeClarify(intent, fit)) {
                return new Plan(Mode.DEGRADE_CLARIFY, intent, hasScope, false, true, null,
                        List.of("意图=" + intent + " 无匹配 Tool", "可澄清改问或补条件"));
            }
            return refuse(intent, hasScope, fit,
                    refuseReasonFor(intent, fit),
                    details(intent, fit, "无匹配 Tool", "无可用兜底"));
        }

        // —— 列表/汇总缺范围：澄清兜底 ——
        if (!hasScope && !clarifyReply
                && (AiQueryIntents.LIST.equals(intent)
                || AiQueryIntents.AGGREGATE.equals(intent)
                || AiQueryIntents.EXPORT.equals(intent))) {
            return new Plan(Mode.CLARIFY_ONLY, intent, false, true, true, null, List.of("缺时间/业务范围"));
        }

        return new Plan(Mode.NORMAL, intent, hasScope, true, true, null, List.of());
    }

    public static boolean hasSuitableTools(String intent, ToolFitness fit) {
        if (fit == null || fit.isEmpty()) {
            return false;
        }
        if (intent == null || AiQueryIntents.UNKNOWN.equals(intent)) {
            return fit.total() > 0;
        }
        return switch (intent) {
            case AiQueryIntents.TREND, AiQueryIntents.COMPARE, AiQueryIntents.RANK, AiQueryIntents.DISTRIBUTION
                    -> fit.dashboard() > 0;
            case AiQueryIntents.LOOKUP, AiQueryIntents.STATUS
                    -> fit.detail() > 0 || fit.pagedList() > 0;
            case AiQueryIntents.LIST, AiQueryIntents.AGGREGATE, AiQueryIntents.EXPLAIN
                    -> fit.pagedList() > 0 || fit.aggregateLike() > 0 || fit.dashboard() > 0 || fit.total() > 0;
            case AiQueryIntents.EXPORT -> fit.exportLike() > 0;
            case AiQueryIntents.ACTION -> fit.actionLike() > 0;
            default -> fit.total() > 0;
        };
    }

    private static boolean canDegradeClarify(String intent, ToolFitness fit) {
        if (fit.isEmpty()) {
            return false;
        }
        // 有任意 Tool 时，多数场景至少能澄清改问；导出/写操作无对应 Tool 则不凑合
        if (AiQueryIntents.EXPORT.equals(intent) || AiQueryIntents.ACTION.equals(intent)) {
            return false;
        }
        if (AiQueryIntents.LOOKUP.equals(intent) || AiQueryIntents.STATUS.equals(intent)) {
            return fit.pagedList() > 0 || fit.total() > 0;
        }
        return true;
    }

    private static Plan refuse(String intent, boolean hasScope, ToolFitness fit,
                               String reason, List<String> details) {
        return new Plan(Mode.REFUSE, intent, hasScope, false, false, reason, details);
    }

    private static String refuseReasonFor(String intent, ToolFitness fit) {
        return switch (intent == null ? "" : intent) {
            case AiQueryIntents.EXPORT -> "这是导出类需求，但当前没有导出/下载类 Tool，无法执行导出。";
            case AiQueryIntents.ACTION -> "这是写操作类需求，但当前没有审批/变更类 Tool，无法执行操作。";
            case AiQueryIntents.LOOKUP, AiQueryIntents.STATUS
                    -> "这是单条查询/状态类问题，但当前没有详情或列表类 Tool，无法查询。";
            case AiQueryIntents.LIST -> "这是明细列表类问题，但当前没有可用的列表/查询 Tool。";
            case AiQueryIntents.AGGREGATE -> "这是汇总类问题，但当前没有汇总或可查询类 Tool。";
            default -> "已识别场景「" + intent + "」，但当前没有匹配的 Tool，且没有可执行的兜底方案。";
        } + "（可调用 Tool 数=" + fit.total() + "）";
    }

    private static List<String> details(String intent, ToolFitness fit, String... extra) {
        List<String> d = new ArrayList<>();
        d.add("意图=" + intent);
        d.add("Tool合计=" + fit.total()
                + " 大屏=" + fit.dashboard()
                + " 分页=" + fit.pagedList()
                + " 详情=" + fit.detail()
                + " 导出=" + fit.exportLike()
                + " 写操作=" + fit.actionLike()
                + " 汇总=" + fit.aggregateLike());
        if (extra != null) {
            for (String e : extra) {
                if (e != null && !e.isBlank()) {
                    d.add(e);
                }
            }
        }
        return d;
    }

    public static String promptSection(Plan plan) {
        if (plan == null) {
            return "";
        }
        return switch (plan.mode()) {
            case DASHBOARD_READY -> "\n\n# 兜底策略：大屏可用\n"
                    + "优先「大屏推荐」Tool；被拒则换大屏 Tool 或澄清，禁止明细分页拼数。\n";
            case DEGRADE_CLARIFY -> "\n\n# 兜底策略：无匹配 Tool，降级澄清（必须遵守）\n"
                    + "当前场景没有完全匹配的 Tool。禁止硬查或编造。\n"
                    + "- 向用户说明缺什么能力；\n"
                    + "- 用 " + AiClarifyConstants.FENCE_LANG + " 引导改问仍可支持的方式"
                    + "（如有范围的明细/合计），或让用户换问题；\n"
                    + "- 本轮不要调用不合适的 Tool。\n";
            case CLARIFY_ONLY -> "\n\n# 兜底策略：场景不清/缺条件 → 澄清\n"
                    + "本轮不要拉数。说明需要什么信息，并输出 "
                    + AiClarifyConstants.FENCE_LANG + "。\n";
            case REFUSE -> "\n\n# 兜底策略：拒绝（无合适 Tool 且无兜底）\n"
                    + "必须直接拒绝，不要调 Tool、不要编造数据。\n"
                    + "拒绝原因：" + nullToEmpty(plan.refuseReason()) + "\n"
                    + "细节：" + String.join("；", plan.refuseDetails() == null ? List.of() : plan.refuseDetails()) + "\n";
            case META_ONLY -> "\n\n# 兜底策略：只谈能力\n不要调用业务 Tool。\n";
            case NORMAL -> "\n\n# 兜底策略：常规\n"
                    + "有合适 Tool 时正常调用；分不清就澄清；连续失败则停止并说明原因，禁止空转。\n";
        };
    }

    public static String enrichRefusal(String refusalJson, Plan plan, int refusalCount) {
        String base = refusalJson == null ? "{}" : refusalJson;
        if (refusalCount < 2) {
            return base;
        }
        String msg = "已连续多次 Tool 调用被拒。请停止调用 Tool，向用户说明拒绝/失败原因；"
                + "若仍可通过补充条件继续，再输出 " + AiClarifyConstants.FENCE_LANG
                + "，否则明确拒绝。";
        if (base.endsWith("}")) {
            return base.substring(0, base.length() - 1)
                    + ",\"fallback\":true,\"fallbackCode\":\"" + FALLBACK_STOP + "\","
                    + "\"fallbackMessage\":\"" + escape(msg) + "\"}";
        }
        return "{\"error\":\"" + AiToolQueryGuard.ERROR_CODE + "\",\"fallback\":true,"
                + "\"fallbackCode\":\"" + FALLBACK_STOP + "\",\"fallbackMessage\":\"" + escape(msg) + "\"}";
    }

    /**
     * 编排层最终兜底：澄清表单，或无兜底时的拒绝说明。
     */
    public static String ensureUserFacingFallback(String content, Plan plan, int refusalCount) {
        if (plan == null) {
            return content;
        }
        if (plan.mode() == Mode.REFUSE) {
            return buildRefuseMessage(plan, content);
        }
        boolean need = plan.mode() == Mode.DEGRADE_CLARIFY
                || plan.mode() == Mode.CLARIFY_ONLY
                || refusalCount >= 2;
        if (!need) {
            return content;
        }
        String body = content == null ? "" : content.trim();
        if (body.contains(AiClarifyConstants.FENCE_LANG)) {
            return content;
        }
        // 连续拒调且无法再澄清时 → 拒绝
        if (refusalCount >= 2 && plan.mode() == Mode.NORMAL && !plan.canFallback()) {
            return buildRefuseMessage(new Plan(Mode.REFUSE, plan.intent(), plan.hasScope(), false, false,
                    "多次尝试后仍无法安全完成查询，已拒绝继续调用。",
                    List.of("连续拒调次数=" + refusalCount)), content);
        }
        String tip = switch (plan.mode()) {
            case DEGRADE_CLARIFY -> "当前没有与该场景完全匹配的工具，无法直接按原问题执行。"
                    + "请改问仍支持的方式，或联系管理员配置对应接口。";
            case CLARIFY_ONLY -> "还需要补充条件后才能继续（场景或范围不清晰）。";
            default -> "当前无法安全完成查询。若可补充条件请填写下方表单；否则请更换问法。";
        };
        String clarify = defaultClarifyJson(plan);
        if (body.isBlank()) {
            return tip + "\n\n```" + AiClarifyConstants.FENCE_LANG + "\n" + clarify + "\n```";
        }
        return body + "\n\n" + tip + "\n\n```" + AiClarifyConstants.FENCE_LANG + "\n" + clarify + "\n```";
    }

    /** REFUSE 模式直接返回给用户的文案（可短路径不调 LLM）。 */
    public static String buildRefuseMessage(Plan plan, String priorContent) {
        String reason = plan == null || plan.refuseReason() == null || plan.refuseReason().isBlank()
                ? "当前无法处理该请求：场景不匹配或缺少可用工具，且没有可执行的兜底方案。"
                : plan.refuseReason();
        StringBuilder sb = new StringBuilder();
        sb.append("**已拒绝本次请求**\n\n");
        sb.append("**原因：** ").append(reason).append('\n');
        if (plan != null && plan.refuseDetails() != null && !plan.refuseDetails().isEmpty()) {
            sb.append("\n**说明：**\n");
            for (String d : plan.refuseDetails()) {
                sb.append("- ").append(d).append('\n');
            }
        }
        sb.append("\n你可以：换一种更具体的问法、补充业务范围，或联系管理员为该 Agent 配置匹配的 Tool。");
        if (priorContent != null && !priorContent.isBlank()
                && !priorContent.contains("已拒绝本次请求")) {
            return priorContent.trim() + "\n\n" + sb;
        }
        return sb.toString();
    }

    private static String defaultClarifyJson(Plan plan) {
        if (plan != null && plan.mode() == Mode.DEGRADE_CLARIFY
                && AiQueryIntents.isDashboard(plan.intent())) {
            return """
                    {
                      "id": "fallback_dashboard",
                      "title": "请调整提问方式",
                      "description": "大屏类分析暂无匹配工具，可改问有范围的明细或合计",
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
                  "title": "请补充或确认",
                  "description": "场景不清或缺少条件，请补充后继续",
                  "fields": [
                    {"id":"askType","label":"你更需要","type":"single","required":false,"options":[
                      {"value":"list","label":"明细列表"},
                      {"value":"sum","label":"合计/总数"},
                      {"value":"lookup","label":"按单号查详情"}
                    ]},
                    {"id":"timeRange","label":"时间范围","type":"text","required":false,"placeholder":"例如：最近1个月"},
                    {"id":"bizNo","label":"单号/编号","type":"text","required":false}
                  ]
                }""";
    }

    private static String blob(AiToolDefinition t) {
        return ((t.getToolCode() == null ? "" : t.getToolCode()) + " "
                + (t.getName() == null ? "" : t.getName()) + " "
                + (t.getPath() == null ? "" : t.getPath()) + " "
                + (t.getDescription() == null ? "" : t.getDescription()))
                .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String blob, String... keys) {
        if (blob == null || blob.isEmpty()) {
            return false;
        }
        for (String k : keys) {
            if (k != null && blob.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
