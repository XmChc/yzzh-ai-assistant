package cn.ysmp.ai.constants;

/**
 * ADMIN 渠道允许注册的 OpenAPI 路径规则。
 * <p>拒绝 app-api / sfmp-api / yard-api；允许 /admin-api/** 及宿主扫描到的管理端模块相对路径。
 */
public final class AiAdminChannelPaths {

    private AiAdminChannelPaths() {
    }

    /**
     * @return true 表示可注册到 ADMIN 渠道白名单
     */
    public static boolean isAllowed(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        String lower = p.toLowerCase();
        if (lower.startsWith("/app-api/") || "/app-api".equals(lower)) {
            return false;
        }
        if (lower.startsWith("/sfmp-api/") || "/sfmp-api".equals(lower)) {
            return false;
        }
        if (lower.startsWith("/yard-api/") || "/yard-api".equals(lower)) {
            return false;
        }
        return true;
    }

    public static String rejectReason(String path) {
        return "ADMIN 渠道仅允许 admin-api（或管理端模块路径），拒绝: " + path;
    }
}
