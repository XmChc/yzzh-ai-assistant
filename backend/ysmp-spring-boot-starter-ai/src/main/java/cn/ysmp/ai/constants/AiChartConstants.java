package cn.ysmp.ai.constants;

/**
 * 内置图表（ai-chart）协议：fence 语言标记与支持的图表类型。
 * <p>各类型字段需求说明见 {@link AiChartFormatSpecs}（写入 system_prompt）。
 */
public final class AiChartConstants {

    private AiChartConstants() {
    }

    public static final String FENCE_LANG = "ai-chart";

    public static final String TYPE_BAR = "bar";
    public static final String TYPE_HORIZONTAL_BAR = "horizontal_bar";
    public static final String TYPE_LINE = "line";
    public static final String TYPE_AREA = "area";
    public static final String TYPE_STACKED_BAR = "stacked_bar";
    public static final String TYPE_PIE = "pie";
    public static final String TYPE_DONUT = "donut";
    public static final String TYPE_SCATTER = "scatter";
    public static final String TYPE_RADAR = "radar";
    public static final String TYPE_FUNNEL = "funnel";

    public static final String[] ALL_TYPES = {
            TYPE_BAR, TYPE_HORIZONTAL_BAR, TYPE_LINE, TYPE_AREA, TYPE_STACKED_BAR,
            TYPE_PIE, TYPE_DONUT, TYPE_SCATTER, TYPE_RADAR, TYPE_FUNNEL
    };

    public static final int MAX_CHARTS_PER_REPLY = 6;
    public static final int MAX_CATEGORIES = 32;
    public static final int MAX_SERIES = 8;
    public static final int MAX_PIE_SLICES = 16;
}
