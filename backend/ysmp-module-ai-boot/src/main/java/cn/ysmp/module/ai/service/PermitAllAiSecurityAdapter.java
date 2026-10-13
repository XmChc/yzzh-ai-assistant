package cn.ysmp.module.ai.service;

import cn.ysmp.ai.spi.AiSecurityAdapter;

/**
 * 占位实现：开发联调用。生产宿主应提供真实 AiSecurityAdapter（ysmp 可自动装配替换本 Bean）。
 */
public class PermitAllAiSecurityAdapter implements AiSecurityAdapter {

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        return true;
    }

    @Override
    public Long getLoginUserId() {
        return 0L;
    }

    @Override
    public String getAuthorizationHeader() {
        return null;
    }
}
