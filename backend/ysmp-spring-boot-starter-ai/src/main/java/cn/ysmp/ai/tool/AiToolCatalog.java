package cn.ysmp.ai.tool;

import java.util.List;

/**
 * Tool / Skill 能力目录：由 boot 实现（只读 DB + {@link cn.ysmp.ai.spi.AiSecurityAdapter}）。
 */
public interface AiToolCatalog {

    /**
     * 当前用户在指定 Agent 下可调用的 Tool（已激活 Skill / GENERAL 全量可用等规则已应用）。
     *
     * @param agentCode 可空则默认 Agent
     */
    List<AiToolDefinition> listAvailableTools(String agentCode);

    /**
     * 完整能力快照（含模式、已激活/未激活 Skill、可调用 Tool）。
     *
     * @param skillCodeHint 用户指定 Skill（须已激活）
     * @param toolCodeHint  用户指定 Tool（须 available）
     */
    AiCapabilityBundle resolveCapabilities(String agentCode, String skillCodeHint, String toolCodeHint);

    /**
     * 是否具备「可选该 Agent」的最低能力（短路，供 Agent 列表）。
     */
    boolean hasUsableCapability(String agentCode);
}
