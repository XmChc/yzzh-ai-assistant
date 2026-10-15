/**
 * 宿主 SPI。RBAC 适配默认路径：
 * <ul>
 *   <li>{@link cn.ysmp.ai.spi.AiSecurityAdapter} — 权限码预检 + Token 转发</li>
 *   <li>{@link cn.ysmp.ai.spi.AiEndpointPermissionResolver} — 注册时固化 Tool 权限码</li>
 * </ul>
 * 可选受控查询（无法走 HTTP 汇总 API 时）：
 * <ul>
 *   <li>{@link cn.ysmp.ai.spi.AiQueryCapabilityCatalog} — 按权限码暴露能力（非表名）</li>
 *   <li>{@link cn.ysmp.ai.spi.AiDataScopeProvider} — 强制行级条件</li>
 *   <li>{@link cn.ysmp.ai.spi.AiGovernedQueryExecutor} — 宿主进程内执行</li>
 * </ul>
 * 默认 NoOp；权限单位始终是<strong>权限码</strong>，不是角色名或表名。
 */
package cn.ysmp.ai.spi;
