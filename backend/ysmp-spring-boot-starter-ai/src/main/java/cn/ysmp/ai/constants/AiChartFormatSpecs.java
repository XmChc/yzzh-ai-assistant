package cn.ysmp.ai.constants;

/**
 * 各图表类型的格式需求说明（注入 system_prompt，供模型严格按 schema 输出）。
 */
public final class AiChartFormatSpecs {

    private AiChartFormatSpecs() {
    }

    /** 写入 system_prompt 的完整图表章节。 */
    public static String promptSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 内置图表（必须遵守）\n");
        sb.append("当需要可视化对比、趋势、占比、漏斗等数据时，在 Markdown 正文中插入一个或多个 ")
                .append(AiChartConstants.FENCE_LANG)
                .append(" 代码块（JSON）。前端会按 type 渲染图表。")
                .append("禁止编造数据；数值须来自工具结果或用户已确认信息。")
                .append("单次回答最多 ").append(AiChartConstants.MAX_CHARTS_PER_REPLY).append(" 张图。\n");
        sb.append("通用字段：type（必填）、title（可选）、unit（可选单位文案）。\n");

        sb.append(spec(AiChartConstants.TYPE_BAR,
                "柱状图：分类对比",
                """
                        必填：categories（字符串数组，≤%d）、series（≤%d 个系列）
                        series[i] = {"name":"系列名","data":[与 categories 等长的数字]}
                        示例：
                        {"type":"bar","title":"各区销量","unit":"单","categories":["东","南","西"],"series":[{"name":"本月","data":[12,8,15]}]}
                        """.formatted(AiChartConstants.MAX_CATEGORIES, AiChartConstants.MAX_SERIES)));

        sb.append(spec(AiChartConstants.TYPE_HORIZONTAL_BAR,
                "条形图：横向柱状，适合长分类名",
                """
                        字段同 bar：categories + series[].name/data
                        示例：
                        {"type":"horizontal_bar","title":"工单耗时","unit":"小时","categories":["A线","B线"],"series":[{"name":"平均","data":[3.2,4.1]}]}
                        """));

        sb.append(spec(AiChartConstants.TYPE_LINE,
                "折线图：时间/顺序趋势",
                """
                        必填：categories、series[].name/data（等长数字）
                        可选：smooth（boolean，默认 false）
                        示例：
                        {"type":"line","title":"近7日入库","categories":["周一","周二","周三"],"series":[{"name":"数量","data":[10,14,9]}],"smooth":true}
                        """));

        sb.append(spec(AiChartConstants.TYPE_AREA,
                "面积图：强调累积/量级趋势",
                """
                        字段同 line：categories + series[].name/data，可选 smooth
                        示例：
                        {"type":"area","title":"库存水位","unit":"件","categories":["W1","W2","W3"],"series":[{"name":"库存","data":[120,150,130]}]}
                        """));

        sb.append(spec(AiChartConstants.TYPE_STACKED_BAR,
                "堆叠柱状图：部分与总体",
                """
                        必填：categories、series（多个系列 data 等长，同位置数值会堆叠）
                        示例：
                        {"type":"stacked_bar","title":"订单构成","categories":["1月","2月"],"series":[{"name":"线上","data":[30,40]},{"name":"线下","data":[20,15]}]}
                        """));

        sb.append(spec(AiChartConstants.TYPE_PIE,
                "饼图：占比构成",
                """
                        必填：data（数组，≤%d）每项 {"name":"名称","value":正数}
                        不要同时使用 categories/series
                        示例：
                        {"type":"pie","title":"缺陷占比","unit":"%%","data":[{"name":"外观","value":35},{"name":"功能","value":45},{"name":"其他","value":20}]}
                        """.formatted(AiChartConstants.MAX_PIE_SLICES)));

        sb.append(spec(AiChartConstants.TYPE_DONUT,
                "环图：占比（中空饼图）",
                """
                        字段同 pie：data[].name/value
                        示例：
                        {"type":"donut","title":"产能利用率","data":[{"name":"已用","value":72},{"name":"空闲","value":28}]}
                        """));

        sb.append(spec(AiChartConstants.TYPE_SCATTER,
                "散点图：两维关系",
                """
                        必填：series[].name、series[].points（[[x,y],...] 数字对）
                        可选：xLabel、yLabel
                        示例：
                        {"type":"scatter","title":"工时-不良","xLabel":"工时","yLabel":"不良数","series":[{"name":"批次","points":[[2,1],[4,3],[5,2]]}]}
                        """));

        sb.append(spec(AiChartConstants.TYPE_RADAR,
                "雷达图：多维指标对比",
                """
                        必填：indicators（[{"name":"指标","max":上限数字},...]）、series[].name/data（与 indicators 等长）
                        示例：
                        {"type":"radar","title":"供应商评分","indicators":[{"name":"质量","max":100},{"name":"交期","max":100},{"name":"价格","max":100}],"series":[{"name":"甲","data":[90,80,70]}]}
                        """));

        sb.append(spec(AiChartConstants.TYPE_FUNNEL,
                "漏斗图：转化层级",
                """
                        必填：data（从上到下，[{"name":"阶段","value":正数},...]），value 一般递减
                        示例：
                        {"type":"funnel","title":"询价转化","data":[{"name":"询价","value":1000},{"name":"报价","value":400},{"name":"成交","value":80}]}
                        """));

        sb.append("输出时：先用简短文字说明图表含义，再输出代码块；不要把图表 JSON 写进普通 markdown 表格。\n");
        return sb.toString();
    }

    private static String spec(String type, String title, String body) {
        return "\n### type=`" + type + "` — " + title + "\n" + body.trim() + "\n";
    }
}
