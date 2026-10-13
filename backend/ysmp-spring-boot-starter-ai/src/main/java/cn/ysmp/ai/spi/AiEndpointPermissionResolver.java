package cn.ysmp.ai.spi;

import java.util.List;

/**
 * 宿主扩展：按 HTTP method + path 解析接口所需权限码。
 * <p>仅在 Tool 固化时机（注册 / resync / 启动回填）调用；对话热路径只读 DB。
 */
public interface AiEndpointPermissionResolver {

    /**
     * @return 权限码列表；未知或不适用返回空列表（表示 AI 侧不额外门禁）
     */
    List<String> resolve(String httpMethod, String path);
}
