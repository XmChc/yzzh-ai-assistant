package cn.ysmp.ai.spi;

import java.util.List;

/**
 * 默认空实现：不解析权限，依赖执行期宿主 API 鉴权。
 */
public class NoOpAiEndpointPermissionResolver implements AiEndpointPermissionResolver {

    @Override
    public List<String> resolve(String httpMethod, String path) {
        return List.of();
    }
}
