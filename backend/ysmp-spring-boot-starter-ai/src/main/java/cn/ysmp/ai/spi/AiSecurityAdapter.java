package cn.ysmp.ai.spi;

/**
 * 宿主 RBAC 安全适配：权限码判断与当前用户 Token 转发。
 * <p>
 * <strong>适配契约（推荐默认）</strong>：
 * <ul>
 *   <li>只对接宿主权限<strong>码</strong>（如 {@code order:query}），不要按角色名或表名鉴权</li>
 *   <li>Catalog 预检：{@link #hasAnyPermissions} 决定 Tool.available</li>
 *   <li>执行期：{@link #getAuthorizationHeader()} 转发到宿主 API，复用宿主拦截器做二次鉴权与行级数据范围</li>
 * </ul>
 * ysmp 体系可自动装配；其他 RBAC 宿主提供本 Bean 即可。
 */
public interface AiSecurityAdapter {

    /**
     * 当前登录用户是否拥有任一权限码（与宿主 RBAC 权限字符一致）。
     */
    boolean hasAnyPermissions(String... permissions);

    /**
     * 当前用户 ID；未登录返回 null。
     */
    Long getLoginUserId();

    /**
     * 调用宿主业务 API 时需要转发的 Authorization 头值（含 Bearer 前缀，可为空）。
     * <p>有此头时宿主 {@code @PreAuthorize} / 数据权限拦截器按原 Web 请求生效。
     */
    String getAuthorizationHeader();
}
