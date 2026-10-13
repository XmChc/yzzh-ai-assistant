package cn.ysmp.ai.tool;

import cn.ysmp.ai.constants.AiToolTypes;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 发给模型的 Tool 定义（Builtin 或 OpenAPI 勾选合并后）。
 */
@Data
@Builder
public class AiToolDefinition {

    /** @deprecated 使用 {@link AiToolTypes#BUILTIN} */
    @Deprecated
    public static final String TYPE_BUILTIN = AiToolTypes.BUILTIN;
    /** @deprecated 使用 {@link AiToolTypes#OPENAPI} */
    @Deprecated
    public static final String TYPE_OPENAPI = AiToolTypes.OPENAPI;

    private String toolCode;
    /** {@link AiToolTypes} */
    private String toolType;
    private String name;
    private String description;
    private List<String> requiredPermissions;
    /** OPENAPI */
    private String httpMethod;
    private String path;
    private String module;
    private Map<String, Object> parametersSchema;
    /** 出参 JSON Schema（成功响应）；仅供提示与结果压缩，非 OpenAI 标准字段 */
    private Map<String, Object> responseSchema;
    /** 当前用户是否可用（只读 DB 权限码判定，非 SPI） */
    private boolean available;
    /** 不可用原因（如缺少的权限码） */
    private String unavailableReason;
}
