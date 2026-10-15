package cn.ysmp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SDK 技术项开关（模型凭据在管理端表，不在此配置）。
 */
@Data
@ConfigurationProperties(prefix = "ysmp.ai")
public class AiProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 内调宿主 API 的基址 */
    private String internalBaseUrl = "http://127.0.0.1:58080";

    /**
     * 进 LLM 前的 Tool 响应硬上限（压缩失败时兜底截断）。
     * 全量暂存上限见 {@link #maxToolStoreChars}。
     */
    private int maxToolResponseChars = 32000;

    /** Tool 全量结果暂存字符上限（超过则仍截断后再解析） */
    private int maxToolStoreChars = 512_000;

    /** 行数达到该阈值时压缩进模并启用结果面板分页 */
    private int toolResultCompactThresholdRows = 20;

    /** 压缩摘要中保留的样例行数 */
    private int toolResultSampleRows = 5;

    /** 结果面板默认每页行数 */
    private int toolResultPageSizeDefault = 10;

    /** 暂存 TTL（毫秒） */
    private long toolResultTtlMs = 1_800_000L;

    /**
     * Agent/Skill/Tool 配置快照缓存 TTL（毫秒）。对话热路径只读内存快照，不打库。
     * 管理端写操作会主动 invalidate；TTL 用于兜底。
     */
    private long catalogCacheTtlMs = 15_000L;

    /** 模型配置缓存 TTL（毫秒） */
    private long llmConfigCacheTtlMs = 30_000L;

    /** 上下文窗口上限（token，用于用量百分比；与模型实际窗口对齐即可） */
    private int contextWindowTokens = 128_000;

    /** 是否启用历史摘要压缩（溢出旧轮次 → 滚动摘要） */
    private boolean summaryEnabled = true;

    /** 压缩后保留的最近原文消息条数 */
    private int summaryKeepRecentMessages = 12;

    /** 未摘要消息达到该条数时触发压缩 */
    private int summaryTriggerMessages = 20;

    /** 滚动摘要最大字符数 */
    private int summaryMaxChars = 2500;

    /**
     * 安全：未提供 {@code AiSecurityAdapter} 时是否启用「全放行」占位（仅本地联调）。
     * 生产务必 {@code false} 并由宿主提供真实适配器。
     */
    private Security security = new Security();

    /**
     * 写操作 Tool 是否必须用户确认后才执行（默认 true）。
     */
    private boolean requireWriteConfirm = true;

    @Data
    public static class Security {
        private boolean permitAllWhenMissing = false;
    }
}
