package cn.ysmp.ai.tool.result;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool 结果覆盖率：本批行数 vs 接口声明总数。
 * <p>汇总意图允许报告本批 stats，但必须带 coverage；禁止引擎多页 N+1 拉全量。
 */
public final class AiToolResultCoverage {

    /** 默认策略：只使用单次 Tool 返回的本批行，不做翻页拼接 */
    public static final String POLICY_SINGLE_BATCH = "SINGLE_BATCH_NO_N_PLUS_1";

    private AiToolResultCoverage() {
    }

    public static boolean isComplete(int coveredRows, Integer reportedTotal) {
        return reportedTotal == null || reportedTotal <= coveredRows;
    }

    public static Map<String, Object> toMap(int coveredRows, Integer reportedTotal) {
        boolean complete = isComplete(coveredRows, reportedTotal);
        Map<String, Object> m = new LinkedHashMap<>(6);
        m.put("coveredRows", coveredRows);
        if (reportedTotal != null) {
            m.put("reportedTotal", reportedTotal);
            m.put("uncoveredRows", Math.max(0, reportedTotal - coveredRows));
        }
        m.put("complete", complete);
        m.put("policy", POLICY_SINGLE_BATCH);
        return m;
    }

    public static String note(String refId, int coveredRows, Integer reportedTotal, boolean complete) {
        if (complete) {
            return "完整结果已暂存 refId=" + refId
                    + "。coverage.complete=true：本批已覆盖筛选范围内全部行；"
                    + "明细用 sample/结果面板；汇总可用 stats 列 sum/avg。"
                    + "禁止再要全量原始 JSON；禁止为凑全量而多次翻页调用同一列表 Tool。";
        }
        return "结果已暂存 refId=" + refId
                + "。coverage.complete=false：已覆盖 " + coveredRows
                + "/" + reportedTotal + " 行，本批 stats 的 sum/avg 仅代表已拉取部分。"
                + "回答汇总时必须同时给出「已覆盖行数/声明总数」与本批合计，禁止说成范围内全量总额；"
                + "可建议用户缩小时间范围或改用宿主聚合/汇总 Tool。"
                + "禁止多页循环加总（N+1）。明细可继续在结果面板翻页查看。";
    }
}
