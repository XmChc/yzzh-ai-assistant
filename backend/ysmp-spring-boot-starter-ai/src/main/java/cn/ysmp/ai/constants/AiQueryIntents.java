package cn.ysmp.ai.constants;

import java.util.Set;

/**
 * 用户问题查询意图。趋势/对比/排行/分布属大屏参数类，应优先大屏相关 Tool。
 */
public final class AiQueryIntents {

    private AiQueryIntents() {
    }

    /** 明细/列表（可分页） */
    public static final String LIST = "LIST";

    /** 单值汇总/合计（总额、总数；非大屏多维） */
    public static final String AGGREGATE = "AGGREGATE";

    /** 单条定位（按单号/ID） */
    public static final String LOOKUP = "LOOKUP";

    /** 是否存在 / 状态询问 */
    public static final String STATUS = "STATUS";

    /** 趋势（大屏） */
    public static final String TREND = "TREND";

    /** 对比（大屏） */
    public static final String COMPARE = "COMPARE";

    /** 排行 TopN（大屏） */
    public static final String RANK = "RANK";

    /** 分布/占比（大屏） */
    public static final String DISTRIBUTION = "DISTRIBUTION";

    /** 导出/全量下载 */
    public static final String EXPORT = "EXPORT";

    /** 原因解释 */
    public static final String EXPLAIN = "EXPLAIN";

    /** 写操作（审批/作废等） */
    public static final String ACTION = "ACTION";

    /** 能力/怎么用等元问题 */
    public static final String META = "META";

    /** 槽位严重不足，应先澄清 */
    public static final String CLARIFY_NEEDED = "CLARIFY_NEEDED";

    /** 无法可靠判定 */
    public static final String UNKNOWN = "UNKNOWN";

    /** 大屏参数类意图：须优先大屏/看板/指标类 Tool，禁止用明细分页拼数 */
    public static final Set<String> DASHBOARD = Set.of(TREND, COMPARE, RANK, DISTRIBUTION);

    public static boolean isDashboard(String intent) {
        return intent != null && DASHBOARD.contains(intent);
    }
}
