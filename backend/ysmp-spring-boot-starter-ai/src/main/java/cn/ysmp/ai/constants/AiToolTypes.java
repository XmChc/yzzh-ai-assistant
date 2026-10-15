package cn.ysmp.ai.constants;

/**
 * Tool 类型常量。
 */
public final class AiToolTypes {

    private AiToolTypes() {
    }

    public static final String BUILTIN = "BUILTIN";
    public static final String OPENAPI = "OPENAPI";
    /** 宿主受控查询能力（经 {@link cn.ysmp.ai.spi.AiGovernedQueryExecutor}） */
    public static final String GOVERNED = "GOVERNED";
}
