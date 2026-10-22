package cn.ysmp.ai.guard;

import cn.ysmp.ai.constants.AiPermissionConstants;
import cn.ysmp.ai.spi.AiSecurityAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * 门禁辅助：基于 {@link AiPermissionConstants} 与宿主 {@link AiSecurityAdapter} 的统一校验入口。
 * <p>业务代码优先调用本类，避免散落权限码字符串。
 * <p>RBAC：此处只做权限<strong>码</strong>预检；行级数据范围依赖执行期转发 Token 后的宿主拦截器。
 */
public final class AiAccessGuard {

    private AiAccessGuard() {
    }

    public static boolean canUseAssistant(AiSecurityAdapter security) {
        return security != null
                && security.hasAnyPermissions(AiPermissionConstants.ASSISTANT_QUERY);
    }

    public static boolean canAccessAgent(AiSecurityAdapter security, String accessPermission) {
        if (security == null) {
            return false;
        }
        if (accessPermission == null || accessPermission.isBlank()) {
            return canUseAssistant(security);
        }
        return security.hasAnyPermissions(accessPermission);
    }

    /**
     * 权限列表是否含至少一条非空权限码。
     * <p>OPENAPI Tool 空列表 = 未绑定宿主权限，Catalog 不可用（fail-closed）。
     */
    public static boolean hasBoundPermissionCodes(Iterable<String> permissions) {
        if (permissions == null) {
            return false;
        }
        for (String p : permissions) {
            if (p != null && !p.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * OPENAPI Tool 是否对当前用户 available：须已绑定非空权限码，且用户具备全部码。
     */
    public static boolean isOpenApiToolAvailable(AiSecurityAdapter security,
                                                 Iterable<String> permissions,
                                                 Map<String, Boolean> memo) {
        if (!hasBoundPermissionCodes(permissions)) {
            return false;
        }
        return hasAllPermissions(security, permissions, memo);
    }

    /**
     * 是否具备全部权限码（空列表视为通过；勿用于 OPENAPI Tool 预检，见 {@link #isOpenApiToolAvailable}）。
     */
    public static boolean hasAllPermissions(AiSecurityAdapter security, Iterable<String> permissions) {
        return hasAllPermissions(security, permissions, null);
    }

    /**
     * 带同请求内 memo，避免每个 Tool 重复问宿主同一权限码。
     */
    public static boolean hasAllPermissions(AiSecurityAdapter security,
                                            Iterable<String> permissions,
                                            Map<String, Boolean> memo) {
        if (security == null) {
            return false;
        }
        if (permissions == null) {
            return true;
        }
        for (String p : permissions) {
            if (p == null || p.isBlank()) {
                continue;
            }
            boolean ok;
            if (memo != null) {
                Boolean hit = memo.get(p);
                if (hit != null) {
                    ok = hit;
                } else {
                    ok = security.hasAnyPermissions(p);
                    memo.put(p, ok);
                }
            } else {
                ok = security.hasAnyPermissions(p);
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 创建本轮对话/解析用的权限 memo。 */
    public static Map<String, Boolean> newPermissionMemo() {
        return new HashMap<>();
    }
}
