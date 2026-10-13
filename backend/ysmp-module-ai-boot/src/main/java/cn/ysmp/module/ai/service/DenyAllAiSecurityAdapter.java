package cn.ysmp.module.ai.service;

import cn.ysmp.ai.spi.AiSecurityAdapter;

/**
 * 安全默认：无宿主适配器且未开启 permit-all 时拒绝一切权限。
 */
public class DenyAllAiSecurityAdapter implements AiSecurityAdapter {

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        return false;
    }

    @Override
    public Long getLoginUserId() {
        return null;
    }

    @Override
    public String getAuthorizationHeader() {
        return null;
    }
}
