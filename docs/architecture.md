# AI 助手 SDK 架构

## 模块关系

```text
宿主应用 (SFMP / Yard / …)
    │  Maven: ysmp-module-ai-boot
    │  npm:   @ysmp/ai-assistant-core + ui
    ▼
┌─────────────────────────────────────────┐
│ ysmp-module-ai-boot（开箱）               │
│  Controller / JDBC / OpenAPI 勾选        │
│  Skill·Agent 管理 / 权限固化 / 安全占位   │
└─────────────────┬───────────────────────┘
                  │ 依赖
┌─────────────────▼───────────────────────┐
│ ysmp-spring-boot-starter-ai（引擎）       │
│  SPI · 编排 · LLM · Tool 执行            │
│  无表、无业务 Controller                  │
└─────────────────────────────────────────┘
```

## 能力模型

```text
Agent (SCENARIO|GENERAL)
  └─ Skill 激活 ⇔ 绑定 Tool 全部 available
       └─ Tool.required_permissions（固化）∩ 用户角色权限
```

### Skill 激活

绑定的每个 Tool 均为 `available`（用户具备全部非空 `required_permissions`）才激活；未激活则**不**部分下发其 Tool。

### Agent 模式

| 模式 | 可调用 Tools | 缺权提示 |
|------|--------------|----------|
| **SCENARIO** | Agent 下已激活 Skill 的 Tools | system 短清单（未激活 Skill + 缺权） |
| **GENERAL** | 用户全部 available Tool；已激活 Skill 作路由名录 | 不报缺权 |

### Tool 权限固化

SPI `AiEndpointPermissionResolver`（宿主可替换；ysmp 默认可解析 `@PreAuthorize`）仅在以下时机**同步**写入 `ai_tool_api.required_permissions`（**禁止**管理端手填权限码 / Schema）：

1. OpenAPI 勾选注册（同时写入双 Schema）  
2. `POST /admin-api/ai/tool-api/resync-permissions`（从宿主注解刷新权限）  
3. `POST /admin-api/ai/tool-api/resync-schemas`（从 OpenAPI 刷新 Schema）  
4. 应用启动 `ApplicationRunner` 同步权限  

对话热路径**不调 SPI**，只读 DB + `AiSecurityAdapter`。

### 角色绑定（宿主 RBAC）

| 权限码 | 含义 |
|--------|------|
| `ai:assistant:query` | 打开助手 |
| `ai:agent:{code}` | Agent 访问资格 |
| Tool 业务码 | 决定 Tool.available → Skill 是否激活 |
| 管理端 CRUD 码 | 仅配置页 |

可选 Agent = `access_permission` **且** 至少 1 个可用能力。

### 聚合 Tool 选型（P2）

OpenAPI 描述/路径含 `汇总|合计|sum|total|stat|aggregate` 等（见 `AiAggregateToolMarkers`）时视为聚合 Tool；`AGGREGATE` 意图下前置排序并标注「【汇总推荐】」。宿主应优先提供带业务权限码的汇总 HTTP 接口并勾选注册。

### RBAC 适配原则（默认最优路径）

助手**只适配宿主 RBAC 权限码**，不按表名、不按角色名鉴权：

```text
宿主: User → Role → PermissionCode
助手: Tool.required_permissions ← 与 @PreAuthorize 同一套码
运行: Catalog 预检(hasPermission) + 执行时转发 Authorization → 宿主拦截器二次鉴权/数据范围
```

| 做 | 不做 |
|----|------|
| Tool/Capability 绑定权限码 | 表级 ACL、助手直连业务库 |
| OpenAPI Tool + Token 复用宿主拦截器 | 在引擎内复制数据权限 SQL 改写 |
| 缺汇总能力时在宿主补聚合 API 再注册为 Tool | 默认多页 N+1 拼合计 |

行级数据范围（本部门/本人）仍由宿主在 API 内处理；助手不解析角色数据权限。

### 汇总（AGGREGATE）与覆盖率

1. **优先**宿主聚合/汇总 Tool（一次返回合计，权限码同业务查询码）。  
2. **仅有分页 Tool** 时：只允许有界单页；压缩结果带 `coverage`（`coveredRows` / `reportedTotal` / `complete` / `policy=SINGLE_BATCH_NO_N_PLUS_1`）。  
3. `complete=true`：可用本批 `stats` 作为范围内结论。  
4. `complete=false`：可报本批合计，**必须**同时说明已覆盖行数/声明总数；禁止说成全量；**禁止**引擎/模型多页循环加总。

### 对话编排

- **tool_call 多轮**：`AiChatOrchestrator` 解析模型 `tool_calls` → 执行 Tool → 回写 messages，上限 `AiLimits.MAX_TOOL_ROUNDS`。
- **SSE**：`POST /admin-api/ai/assistant/chat/stream`，事件见 `AiSseEventTypes`（meta / tool_call / tool_result / delta / done / error）。
- **system_prompt**：Agent 级 + 已激活 Skill 的 `system_prompt` 注入同一 system 消息。

### 对话 @ 引用

输入 `@` 后 token 须一直是 `agent` / `skill` / `tool` 的前缀（忽略大小写）才保持选择框；一旦不像该词则关闭。选中后以芯片 + `agentCode` / `skillCode` / `toolCode` 提交。

## OpenAPI Tool 白名单

1. 扫描 springdoc：GET + POST（按模块分组）  
2. 勾选写入 `ai_tool_api` 并启用；编排仅暴露已注册且启用的 Tool  
3. 注册时从 OpenAPI 抽取 **入参** `param_schema_json`（path/query + requestBody 扁平合并）与 **出参** `response_schema_json`（2xx `application/json`，`$ref` 本地展开）  
4. 发给模型：`parameters` = 入参 Schema；出参精简后拼入 description 的 `Returns:` 块（无标准 returns 字段）  
5. 大结果压缩优先按出参 Schema 定位列表路径与列名，否则回退启发式  
6. 管理端 `POST .../resync-schemas` 可按当前 OpenAPI 回填已注册 Tool 的双 Schema  

## 安全

1. 宿主提供 `AiSecurityAdapter`（推荐）  
2. 未提供：默认 DenyAll；`ysmp.ai.security.permit-all-when-missing=true` 才 PermitAll  

## 演进路线（已定方向）

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | RBAC：权限码 + Tool available + Token 转发 | 已有 |
| P1 | AGGREGATE：`coverage` 单批合计话术；禁止默认 N+1 | 已落地 |
| P2 | 聚合 Tool 识别/`【汇总推荐】`优先；宿主注册汇总 OpenAPI | 已落地（宿主补 API） |
| P3 | 写操作 SSE `tool_confirm` + 前端确认条（`confirmWriteTools`） | 已落地 |
| P4 | `AiQueryCapabilityCatalog` + `AiDataScopeProvider` + `AiGovernedQueryExecutor` | SPI 已落地（默认 NoOp） |

### 写操作确认

命中 `AiWriteToolMarkers` 的 Tool 默认不执行，推送 SSE `tool_confirm`；前端确认后带 `confirmWriteTools=true` 重发同一问题。可用 `ysmp.ai.require-write-confirm=false` 关闭。

### 受控查询 SPI（可选）

宿主实现三个 Bean 后，能力会合并进本轮 tools（`toolType=GOVERNED`）：

1. `AiQueryCapabilityCatalog.listAvailable()` — 已按权限码过滤  
2. `AiDataScopeProvider.resolve(code)` — 强制行级条件  
3. `AiGovernedQueryExecutor.execute(...)` — 同进程执行官方查询  

默认均为 NoOp；优先仍用 OpenAPI + Token。

## 前端

| 包 | 职责 |
|----|------|
| core | `createAiAssistant`、`api`、`AiPermissions` |
| ui | 浮动对话（@ 引用）+ 配置页 |
