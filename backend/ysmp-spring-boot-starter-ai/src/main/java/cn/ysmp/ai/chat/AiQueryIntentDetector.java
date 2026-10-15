package cn.ysmp.ai.chat;

import cn.ysmp.ai.constants.AiClarifyConstants;
import cn.ysmp.ai.constants.AiQueryIntents;
import cn.ysmp.ai.tool.AiToolDefinition;
import cn.ysmp.ai.tool.AiToolKindHints;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 从用户问题粗判查询意图，并生成本轮 system 提示。
 * 趋势/对比/排行/分布 → 大屏参数类，优先大屏相关 Tool。
 */
public final class AiQueryIntentDetector {

    private static final Pattern META = Pattern.compile(
            "你能(做什么|查什么|干什么)|有哪些(能力|功能|技能)|怎么用|如何使用|你会什么|"
                    + "what can you",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION = Pattern.compile(
            "帮我(审批|作废|取消|提交|确认|驳回|关闭|开启|启用|停用|删除|创建|修改|更新|保存)|"
                    + "请(审批|作废|取消|提交)|执行(操作|审批)|"
                    + "\\b(approve|reject|cancel|submit|delete|create|update)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPORT = Pattern.compile(
            "导出|下载(全量|全部|明细|excel|csv)?|全量(下载|导出)|生成\\s*(excel|csv)|"
                    + "\\b(export|download)\\s*(excel|csv|all)?\\b",
            Pattern.CASE_INSENSITIVE);

    /** 须带单号/ID 形态，避免单独「详情」误伤列表问法 */
    private static final Pattern LOOKUP = Pattern.compile(
            "(订单号|单号|编号|流水号|工单号|车牌|手机号)\\s*[是:=：]?\\s*\\S+"
                    + "|\\b(id|ID)\\s*[:=：]?\\s*\\d+"
                    + "|\\b(SO|PO|WO|NO)[-_]?\\d{3,}"
                    + "|打开(这|该)单|查(一下|下)?这(个|笔|条)单",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STATUS = Pattern.compile(
            "(有没有|到哪了|什么状态|到哪个环节|发货了吗|完成了吗|审批了吗|存在吗|是否存在)|"
                    + "状态(是|怎么样|如何)|"
                    + "\\b(status of|does it exist)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TREND = Pattern.compile(
            "趋势|走势|按(日|周|月|年)|逐(日|月|周)|时间序列|"
                    + "近\\s*\\d+\\s*(天|日|周|月|年).{0,12}(趋势|走势|变化|曲线)|"
                    + "每月|每天|每周.*(量|额|数|趋势)|"
                    + "\\b(trend|time series|over time)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPARE = Pattern.compile(
            "对比|比较|相比|vs\\.?|versus|"
                    + "(同比|环比).{0,6}(对比|比较|差异|变化)|"
                    + "(本月|上月|今年|去年|本期|上期).{0,10}(比|对比|差异|相比)|"
                    + "\\b(compare|comparison|yoy|mom)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANK = Pattern.compile(
            "排行|排名|榜单|top\\s*\\d+|前\\s*\\d+\\s*(名|位|个|条)|"
                    + "(销量|金额|数量|订单).{0,6}(最高|最低|最多|最少)|"
                    + "(最高|最低|最多|最少).{0,6}(的)?(客户|供应商|商品|物料|司机|车辆|门店)|"
                    + "\\b(rank|ranking|top\\s*\\d+)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DISTRIBUTION = Pattern.compile(
            "分布|占比|构成|结构比例|饼图|各状态|分类统计|百分比构成|"
                    + "\\b(distribution|proportion|breakdown|pie)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AGGREGATE = Pattern.compile(
            "汇总|合计|总计|总数|总额|总量|总和|金额总|总金额|总数量|总笔数|一共(多少|几)|"
                    + "平均|均值|(同比|环比)(增长|增幅|多少)|"
                    + "\\b(sum|total|aggregate|average|avg|count)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LIST = Pattern.compile(
            "列表|有哪些|列出|分页|哪些(订单|单据|记录|客户)|"
                    + "查(一下|下|询)?(最近|本月|今日|所有)?.{0,8}(订单|单据|记录|列表)|"
                    + "\\b(list|show me)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPLAIN = Pattern.compile(
            "为什么|原因|怎么回事|为何|解释一下|分析原因|"
                    + "\\b(why|reason|explain)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HAS_SCOPE = Pattern.compile(
            "最近|近\\s*\\d+|本月|上月|今天|今日|昨日|本年|今年|去年|本周|上周|"
                    + "\\d{4}[-/.年]\\d{1,2}|至|之间|区间|从.+到",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HAS_BIZ_OBJECT = Pattern.compile(
            "订单|单据|工单|客户|车辆|司机|库存|物料|合同|发票|运单|记录|"
                    + "\\b(order|customer|sku|invoice)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String DASHBOARD_TOOL_HINT =
            "此类问题属于大屏参数类：必须优先选用已标注「大屏推荐」的 Tool"
                    + "（或名称/描述含 大屏、dashboard、看板、指标、chart、stat 等）；"
                    + "禁止用明细分页 Tool 翻页拼趋势/对比/排行/分布。"
                    + "若无可用大屏 Tool，说明能力不足并用 "
                    + AiClarifyConstants.FENCE_LANG + " 引导，不要硬查全表。";

    private AiQueryIntentDetector() {
    }

    public record Detection(String intent, boolean hasScope, boolean clarifyReply) {
    }

    public static Detection analyze(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new Detection(AiQueryIntents.UNKNOWN, false, false);
        }
        String raw = userMessage.trim();
        boolean clarifyReply = raw.startsWith(AiClarifyConstants.REPLY_PREFIX);
        String text = clarifyReply
                ? raw.substring(AiClarifyConstants.REPLY_PREFIX.length()).trim()
                : raw;
        if (text.isBlank()) {
            text = raw;
        }
        boolean hasScope = HAS_SCOPE.matcher(text).find();
        String intent = detectIntent(text, hasScope, clarifyReply);
        return new Detection(intent, hasScope, clarifyReply);
    }

    /** @deprecated 使用 {@link #analyze(String)} */
    public static String detect(String userMessage) {
        return analyze(userMessage).intent();
    }

    private static String detectIntent(String text, boolean hasScope, boolean clarifyReply) {
        if (META.matcher(text).find()) {
            return AiQueryIntents.META;
        }
        if (ACTION.matcher(text).find()) {
            return AiQueryIntents.ACTION;
        }
        if (EXPORT.matcher(text).find()) {
            return AiQueryIntents.EXPORT;
        }
        if (LOOKUP.matcher(text).find()) {
            return AiQueryIntents.LOOKUP;
        }
        if (STATUS.matcher(text).find()) {
            return AiQueryIntents.STATUS;
        }

        // 大屏参数类优先于笼统汇总
        if (TREND.matcher(text).find()) {
            return AiQueryIntents.TREND;
        }
        if (COMPARE.matcher(text).find()) {
            return AiQueryIntents.COMPARE;
        }
        if (RANK.matcher(text).find()) {
            return AiQueryIntents.RANK;
        }
        if (DISTRIBUTION.matcher(text).find()) {
            return AiQueryIntents.DISTRIBUTION;
        }

        if (EXPLAIN.matcher(text).find()) {
            return AiQueryIntents.EXPLAIN;
        }

        if (AGGREGATE.matcher(text).find()) {
            return AiQueryIntents.AGGREGATE;
        }
        if (LIST.matcher(text).find()) {
            return AiQueryIntents.LIST;
        }
        if (HAS_BIZ_OBJECT.matcher(text).find() && hasScope) {
            return AiQueryIntents.LIST;
        }

        // 补充说明轮次：用户已在填槽，不再强制 CLARIFY_NEEDED
        if (!clarifyReply && HAS_BIZ_OBJECT.matcher(text).find() && !hasScope && text.length() < 40) {
            return AiQueryIntents.CLARIFY_NEEDED;
        }
        return AiQueryIntents.UNKNOWN;
    }

    public static String promptHint(Detection detection, List<String> dashboardToolCodes) {
        if (detection == null) {
            return promptHint(AiQueryIntents.UNKNOWN, List.of(), false);
        }
        return promptHint(detection.intent(), dashboardToolCodes, detection.hasScope());
    }

    public static String promptHint(String intent) {
        return promptHint(intent, List.of(), false);
    }

    public static String promptHint(String intent, List<String> dashboardToolCodes, boolean hasScope) {
        String scopeNote = hasScope ? "（已检测到时间/范围表述，请映射到 Tool 筛选参数）"
                : "（未检测到明确范围：分页/大屏查询前需范围或单号）";
        String dashList = formatDashTools(dashboardToolCodes);

        if (intent == null || AiQueryIntents.UNKNOWN.equals(intent)) {
            return "\n\n# 本轮意图：未判定（UNKNOWN）" + scopeNote + "\n"
                    + "先判断明细/单条/汇总/大屏；缺范围用 ai-clarify。分页禁止无筛选。\n"
                    + dashList;
        }
        return switch (intent) {
            case AiQueryIntents.LIST -> "\n\n# 本轮意图：明细列表（LIST）" + scopeNote + "\n"
                    + "分页展示正确；必须带业务约束；合理 pageSize，明细交给结果面板。\n";
            case AiQueryIntents.AGGREGATE -> "\n\n# 本轮意图：单值汇总（AGGREGATE）" + scopeNote + "\n"
                    + "优先宿主聚合/汇总 Tool（一次返回合计）。"
                    + "仅有分页 Tool 时：只调有界单页，用 coverage + stats 回答；"
                    + "complete=false 时可报本批合计，必须写明已覆盖行数/声明总数，禁止说成全量；"
                    + "禁止多页循环加总（N+1）。\n";
            case AiQueryIntents.LOOKUP -> "\n\n# 本轮意图：单条定位（LOOKUP）\n"
                    + "优先详情/getById Tool；缺唯一键则 ai-clarify。\n";
            case AiQueryIntents.STATUS -> "\n\n# 本轮意图：状态/是否（STATUS）\n"
                    + "优先状态/详情接口，勿拉大列表。\n";
            case AiQueryIntents.TREND -> "\n\n# 本轮意图：趋势（TREND · 大屏）" + scopeNote + "\n"
                    + DASHBOARD_TOOL_HINT + "\n" + dashList
                    + "可用 ai-chart（line/area）。\n";
            case AiQueryIntents.COMPARE -> "\n\n# 本轮意图：对比（COMPARE · 大屏）" + scopeNote + "\n"
                    + DASHBOARD_TOOL_HINT + "\n" + dashList
                    + "可用 ai-chart（bar）。\n";
            case AiQueryIntents.RANK -> "\n\n# 本轮意图：排行（RANK · 大屏）" + scopeNote + "\n"
                    + DASHBOARD_TOOL_HINT + "\n" + dashList
                    + "需要排序+TopN；可用 ai-chart（bar）。\n";
            case AiQueryIntents.DISTRIBUTION -> "\n\n# 本轮意图：分布（DISTRIBUTION · 大屏）" + scopeNote + "\n"
                    + DASHBOARD_TOOL_HINT + "\n" + dashList
                    + "可用 ai-chart（pie/donut）；禁止用一页明细估占比。\n";
            case AiQueryIntents.EXPORT -> "\n\n# 本轮意图：导出（EXPORT）" + scopeNote + "\n"
                    + "优先导出 Tool；禁止多页拼接冒充导出。\n";
            case AiQueryIntents.EXPLAIN -> "\n\n# 本轮意图：原因解释（EXPLAIN）" + scopeNote + "\n"
                    + "先小范围定位再解释；禁止无约束全表扫。\n";
            case AiQueryIntents.ACTION -> "\n\n# 本轮意图：写操作（ACTION）\n"
                    + "先 ai-clarify 确认对象与参数，再调写操作 Tool。\n";
            case AiQueryIntents.META -> "\n\n# 本轮意图：能力询问（META）\n"
                    + "说明能力即可，不要调业务查询 Tool。\n";
            case AiQueryIntents.CLARIFY_NEEDED -> "\n\n# 本轮意图：需先澄清（CLARIFY_NEEDED）\n"
                    + "本轮禁止调用分页/列表/大屏拉取 Tool，直接 "
                    + AiClarifyConstants.FENCE_LANG + " 收集时间范围或单号等。\n";
            default -> "\n\n# 本轮意图：" + intent + scopeNote + "\n"
                    + "按意图选型；分页须约束；大屏类走大屏 Tool。\n" + dashList;
        };
    }

    private static String formatDashTools(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "（当前可调用集中未识别到大屏类 Tool）\n";
        }
        List<String> show = codes.size() > 12 ? codes.subList(0, 12) : codes;
        return "当前可调用的大屏推荐 Tool：" + String.join("、", show)
                + (codes.size() > 12 ? "…" : "") + "\n";
    }

    public static List<String> listDashboardToolCodes(List<AiToolDefinition> tools) {
        List<String> out = new ArrayList<>();
        if (tools == null) {
            return out;
        }
        for (AiToolDefinition t : tools) {
            if (t != null && t.isAvailable() && AiToolKindHints.looksLikeDashboard(t)) {
                out.add(t.getToolCode());
            }
        }
        return out;
    }
}
