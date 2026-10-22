# yzzh-ai-assistant — Agent 编程规范

本文件约束在本工作空间内工作的 AI Agent / 开发者。与 `.cursor/rules/` 规则一并生效。

## 1. 常量与门禁（强制）

- **后端唯一常量源**：`cn.ysmp.ai.constants`（入口说明见 `AiConstants`）。
  - 权限码 → `AiPermissionConstants` / `AiConstants.Permission`
  - Agent 模式 → `AiAgentModes` / `AiConstants.Mode`
  - Tool 类型 → `AiToolTypes` / `AiConstants.ToolType`
  - API 路径 → `AiApiPaths`
  - HTTP 方法 → `AiHttpMethods`
  - 数值上限 → `AiLimits`
  - @ 关键词 → `AiMentionKeywords`
- **门禁校验**：优先 `cn.ysmp.ai.guard.AiAccessGuard`，不要在业务里硬编码 `"ai:assistant:query"` 等字符串。分页/列表 Tool 无业务筛选时由 `AiToolQueryGuard` 拒绝（防全表扫描）。
- **查询意图**：`AiQueryIntents` / `AiQueryIntentDetector`；`TREND|COMPARE|RANK|DISTRIBUTION` 为大屏参数类，优先大屏相关 Tool。
- **查询兜底**：`AiQueryFallback` — 分不清→澄清；无匹配 Tool 可降级→澄清改问；无兜底→明确拒绝原因（`REFUSE`）。
- **前端唯一常量源**：`@ysmp/ai-assistant-core` 的 `constants.ts`（`AiPermissions`、`AiAgentModes`、`AiMentionKeywords` 等），须与后端常量语义对齐。
- **禁止**：在 Controller / Service / UI 中新增魔法字符串（权限码、`SCENARIO`/`GENERAL`、`OPENAPI`、路径前缀、mention 关键词）。新增常量必须先写入 constants 包再引用。

## 2. 模块边界

| 模块 | 职责 | 禁止 |
|------|------|------|
| `ysmp-spring-boot-starter-ai` | SPI、编排、LLM、常量、门禁、Tool 执行 | 不建表、无业务 Controller |
| `ysmp-module-ai-boot` | 表访问、管理 API、OpenAPI 扫描、权限固化 | 不把权限字面量写死在 SQL 拼接以外的业务分支 |
| `ai-assistant-core` | 运行时、API、常量 | 不写 UI |
| `ai-assistant-ui` | UI | 不直接拼权限/模式字符串 |

## 3. 权限与能力模型（勿回退）

- Skill **无**独立 `access_permission`；激活条件：绑定 Tool **全部** `available`。
- Agent：`access_permission` + `agent_mode`（`SCENARIO` | `GENERAL`）。
- Tool 权限 / Schema：**只允许从宿主同步**（注册 / `resync-permissions` / `resync-schemas` / 启动），**禁止**管理端或业务代码手填；**对话热路径不调 SPI**。
- OPENAPI **空权限码 fail-closed**（`available=false`、不可启用；同步自愈停用）。ADMIN 渠道**禁止**注册 app-api / sfmp-api / yard-api；`channel` 默认 ADMIN，APP 预留 opt-in。
- 可选 Agent：`ai:assistant:query` + Agent 权限 + 至少 1 个可用能力。
- **RBAC 适配**：只认宿主权限码（与 `@PreAuthorize` 同源）；Catalog 预检 + 执行转发 Token；禁止表级 ACL / 助手直连业务库当默认路径。
- **AGGREGATE**：优先聚合/GOVERNED Tool；分页仅单批 + `coverage`；禁止多页 N+1 加总。
- **写操作**：命中 `AiWriteToolMarkers` 须 `confirmWriteTools`（SSE `tool_confirm`）；勿在未确认时执行。
- **受控查询 SPI**：`AiQueryCapabilityCatalog` / `AiDataScopeProvider` / `AiGovernedQueryExecutor` 默认 NoOp；能力码非表名。

## 4. 代码风格

- 只改任务相关文件；不顺手大重构、不乱加文档/测试除非被要求。
- Java 17+；前端 Vue 3 + TS，跟随现有包结构。
- Controller `@RequestMapping` 使用 `AiApiPaths.*`（模块路径 `/ai/**`）；宿主加 `/admin-api` 前缀，不要手写完整 URL。
- 与宿主 RBAC 集成走 `AiSecurityAdapter` / `AiEndpointPermissionResolver` SPI，不绑死 `@PreAuthorize` 到 starter。

## 5. 改常量时的同步清单

1. 改 Java `cn.ysmp.ai.constants.*`
2. 同步前端 `frontend/packages/ai-assistant-core/src/constants.ts`
3. 若影响接入说明，更新 `docs/integration.md` / `docs/architecture.md`
4. 本地用 **JDK 17** 执行 `backend` 下 `mvn -DskipTests install`

## 6. 文档入口

- 架构：`docs/architecture.md`
- 接入：`docs/integration.md`
- 本规范：`AGENTS.md`（即工作区 agent 约束；勿删除）
