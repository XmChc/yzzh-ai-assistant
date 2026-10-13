package cn.ysmp.ai.spi;

/**
 * 宿主安全适配：权限判断与当前用户 Token 转发。
 * <p>ysmp 体系可自动装配；其他框架由宿主提供 Bean。
 */
public interface AiSecurityAdapter {

    /**
     * 当前登录用户是否拥有任一权限码。
     */
    boolean hasAnyPermissions(String... permissions);

    /**
     * 当前用户 ID；未登录返回 null。
     */
    Long getLoginUserId();

    /**
     * 调用宿主业务 API 时需要转发的 Authorization 头值（含 Bearer 前缀，可为空）。
     */
    String getAuthorizationHeader();
}
