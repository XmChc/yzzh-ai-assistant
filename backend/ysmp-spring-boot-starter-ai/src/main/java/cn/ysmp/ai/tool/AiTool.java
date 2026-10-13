package cn.ysmp.ai.tool;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builtin Tool 扩展点（少数聚合场景）；主路径为 OpenAPI 勾选，无需实现本接口。
 */
public interface AiTool {

    String getCode();

    String getName();

    String getDescription();

    /**
     * 默认权限预过滤（可空）；权威鉴权仍在业务 API / 安全适配器。
     */
    default List<String> getRequiredPermissions() {
        return Collections.emptyList();
    }

    /**
     * OpenAI function parameters JSON Schema（简化：由实现返回 Map）。
     */
    Map<String, Object> getParametersSchema();

    /**
     * 出参 JSON Schema（可空）；用于提示模型返回形状，非执行校验。
     */
    default Map<String, Object> getResponseSchema() {
        return Collections.emptyMap();
    }

    String execute(Map<String, Object> arguments) throws Exception;
}
