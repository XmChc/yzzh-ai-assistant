package cn.ysmp.ai.constants;

/**
 * 运行时数值上限（编排 / 提示词 token 控制）。
 */
public final class AiLimits {

    private AiLimits() {
    }

    /** SCENARIO 模式 system 中「未激活 Skill」缺权提示最多条数 */
    public static final int MAX_INACTIVE_SKILL_HINTS = 30;

    /** tool_call 多轮上限（含最终回答轮） */
    public static final int MAX_TOOL_ROUNDS = 8;

    /** SSE 推送正文时单段最大字符数（便于前端渐进渲染） */
    public static final int SSE_CONTENT_CHUNK_CHARS = 48;

    /** tool_result 事件预览最大字符数 */
    public static final int TOOL_RESULT_PREVIEW_CHARS = 500;

    /** 拼入 LLM 的历史消息条数上限（user+assistant） */
    public static final int MAX_CONTEXT_MESSAGES = 40;

    /** 历史正文总字符上限（超出从最旧裁剪） */
    public static final int MAX_CONTEXT_CHARS = 48_000;

    /** 默认上下文窗口 token（可被 AiProperties.contextWindowTokens 覆盖） */
    public static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 128_000;

    /** 会话列表默认条数 */
    public static final int DEFAULT_SESSION_LIST_LIMIT = 50;

    /** 摘要压缩后保留的最近原文消息条数 */
    public static final int SUMMARY_KEEP_RECENT_MESSAGES = 12;

    /** 未摘要消息超过该条数时触发压缩（含 keep recent） */
    public static final int SUMMARY_TRIGGER_MESSAGES = 20;

    /** 滚动摘要正文最大字符数 */
    public static final int MAX_SUMMARY_CHARS = 2_500;

    /** Tool 压缩摘要中参与列统计的最大字段数 */
    public static final int TOOL_RESULT_STATS_MAX_FIELDS = 20;

    /** 列统计扫描行数上限（超出则注明 sampled） */
    public static final int TOOL_RESULT_STATS_MAX_SCAN_ROWS = 20_000;

    /** 分类字段 Top-K 取值数 */
    public static final int TOOL_RESULT_STATS_TOP_K = 8;

    /** 分类字段跟踪的最大不同值（超出则停止累加 top） */
    public static final int TOOL_RESULT_STATS_MAX_DISTINCT = 64;

    /** 拼入 Tool description 的 Returns 出参 Schema 最大字符数 */
    public static final int MAX_TOOL_RETURNS_SCHEMA_CHARS = 1_200;

    /** OpenAPI Schema $ref 展开最大深度 */
    public static final int MAX_OPENAPI_SCHEMA_DEPTH = 8;

    /** 分页/列表 Tool 单次 pageSize 上限（超出则截断） */
    public static final int TOOL_QUERY_MAX_PAGE_SIZE = 100;
}
