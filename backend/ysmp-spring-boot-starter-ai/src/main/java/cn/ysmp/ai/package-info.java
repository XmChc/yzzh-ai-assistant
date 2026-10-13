/**
 * AI 助手引擎（SDK 核心，无业务表 / 无 Controller）。
 * <p>
 * 分层约定：
 * <ul>
 *   <li>{@code spi} — 宿主扩展点（安全、模型配置）</li>
 *   <li>{@code tool} — Tool 定义、目录、Builtin、OpenAPI 执行</li>
 *   <li>{@code llm} — OpenAI 兼容客户端</li>
 *   <li>{@code chat} — 对话编排</li>
 *   <li>{@code config} — 自动装配与技术配置（非模型凭据）</li>
 * </ul>
 */
package cn.ysmp.ai;
