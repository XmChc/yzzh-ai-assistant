package cn.ysmp.ai.chat;

import lombok.Data;

/**
 * 对话请求（管理端 / SDK 统一入参）。
 */
@Data
public class AiChatRequest {

    /** 可选会话 ID；空则新建 */
    private String sessionId;
    /** Agent 编码；空则用默认 Agent */
    private String agentCode;
    /** 用户指定 Skill（须已激活） */
    private String skillCode;
    /** 用户指定 Tool（须 available） */
    private String toolCode;
    /** 用户问题 */
    private String message;
}
