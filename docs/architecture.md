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

SPI `AiEndpointPermissionResolver`（宿主可替换；ysmp 默认可解析 `@PreAuthorize`）仅在以下时机写入 `ai_tool_api.required_permissions`：

1. OpenAPI 勾选注册  
2. `POST /admin-api/ai/tool-api/resync-permissions`  
3. 应用启动 `ApplicationRunner` 回填  

对话热路径**不调 SPI**，只读 DB + `AiSecurityAdapter`。

### 角色绑定（宿主 RBAC）

| 权限码 | 含义 |
|--------|------|
| `ai:assistant:query` | 打开助手 |
| `ai:agent:{code}` | Agent 访问资格 |
| Tool 业务码 | 决定 Tool.available → Skill 是否激活 |
| 管理端 CRUD 码 | 仅配置页 |

可选 Agent = `access_permission` **且** 至少 1 个可用能力。

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

## 前端

| 包 | 职责 |
|----|------|
| core | `createAiAssistant`、`api`、`AiPermissions` |
| ui | 浮动对话（@ 引用）+ 配置页 |
