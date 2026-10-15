# AI 助手 SDK 接入说明

## 1. 获取制品

```bash
cd backend && mvn clean install -DskipTests
cd frontend && pnpm install && pnpm --filter @ysmp/ai-assistant-core build
```

## 2. 后端

```xml
<dependency>
  <groupId>cn.ysmp</groupId>
  <artifactId>ysmp-module-ai-boot</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
ysmp:
  ai:
    enabled: true
    internal-base-url: http://127.0.0.1:58080
```

执行 DDL：`backend/ysmp-module-ai-boot/src/main/resources/sql/ai_schema.sql`

已有库升级示例：

```sql
ALTER TABLE `ai_tool_api` ADD COLUMN `module` varchar(128) DEFAULT NULL COMMENT '功能模块' AFTER `operation_id`;
ALTER TABLE `ai_tool_api` ADD COLUMN `response_schema_json` text COMMENT '出参 JSON Schema' AFTER `param_schema_json`;
ALTER TABLE `ai_agent` ADD COLUMN `agent_mode` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT 'SCENARIO|GENERAL' AFTER `access_permission`;
ALTER TABLE `ai_chat_session` ADD COLUMN `context_summary` mediumtext COMMENT '滚动对话摘要' AFTER `title`;
ALTER TABLE `ai_chat_session` ADD COLUMN `summary_until_id` bigint NOT NULL DEFAULT 0 COMMENT '摘要覆盖到的 message.id' AFTER `context_summary`;
```

已注册 Tool 若缺 Schema，可在管理端「同步 Schema」或调用 `POST /admin-api/ai/tool-api/resync-schemas` 按当前 OpenAPI 回填入参/出参。

对话摘要压缩（默认开启）：未摘要消息达到 `summary-trigger-messages`（默认 20）时，将旧轮次压成滚动摘要，热路径只带摘要 + 最近 `summary-keep-recent-messages`（默认 12）条原文。

```yaml
ysmp:
  ai:
    summary-enabled: true
    summary-keep-recent-messages: 12
    summary-trigger-messages: 20
    summary-max-chars: 2500
```

### 澄清补充（ai-clarify）

信息不足时，模型在回答末尾输出 \`\`\`ai-clarify JSON 代码块；编排剥离后通过 SSE `clarify` 事件下发，前端渲染填写/选择表单。用户提交后以「【补充说明】」前缀作为下一轮 user 消息继续对话。字段类型：`text` / `textarea` / `number` / `select` / `single` / `multi`。

### 内置图表（ai-chart）

需要可视化时，模型输出 \`\`\`ai-chart JSON 代码块。`type` 支持：`bar` / `horizontal_bar` / `line` / `area` / `stacked_bar` / `pie` / `donut` / `scatter` / `radar` / `funnel`。各类型字段需求已写入默认 system_prompt（见 `AiChartFormatSpecs`）。前端按 type 用 SVG 渲染，无需额外图表库。

### RBAC 接入要点

1. 实现 `AiSecurityAdapter`：`hasAnyPermissions` 对接宿主权限码；`getAuthorizationHeader` 转发当前用户 Token。  
2. Tool 的 `required_permissions` / Schema **只允许同步、禁止手填**：注册时从 `@PreAuthorize` + OpenAPI 写入；变更后调 `resync-permissions` / `resync-schemas`（或管理页「同步权限码 / 同步 Schema」）。  
3. 对话热路径只读 DB 中的权限码 + Adapter 预检；真正业务鉴权/数据范围在宿主 API 内完成。  
4. 前端 `hasPermission` 与后端权限码保持一致（见 `AiPermissions`）。  
5. **汇总 API（推荐）**：为「月总额」等提供聚合接口，描述中带 `汇总/合计/sum/total` 等词并挂业务权限码，注册为 OpenAPI Tool；AGGREGATE 意图会优先【汇总推荐】。  
6. **写操作确认**：默认开启（`ysmp.ai.require-write-confirm=true`）；前端确认条回传 `confirmWriteTools`。  
7. **受控查询（可选）**：无法提供 HTTP 汇总时，实现 `AiQueryCapabilityCatalog` + `AiDataScopeProvider` + `AiGovernedQueryExecutor`（见 `docs/architecture.md`）。

### Tool 大数据：压缩进模 + 结果分页

Tool 返回行数 ≥ `tool-result-compact-threshold-rows`（默认 20）或体积较大时：

1. 服务端解析结构并**全量暂存本批**（`refId`，TTL 默认 30 分钟）；**不做**多页 N+1 拉取  
2. 进 LLM 的是 `fields` + `stats` + `sample` + `rowCount` + `coverage` 摘要  
   - `stats`：单遍扫描列汇总（数值 min/max/sum/avg；分类 top 频次）  
   - `coverage`：`coveredRows` / `reportedTotal` / `complete`；汇总回答必须带覆盖率  
   - `sample`：少量样例行，供举例与制表  
3. SSE `tool_data` 通知前端；结果面板展示列汇总 + 分页明细，调用  
   `/ai/assistant/tool-result/page`、`/tool-result/field`（嵌套数组）

**查询安全与意图**（`AiQueryIntentDetector` / `AiQueryFallback` / `AiToolQueryGuard`）：
- 有范围允许（`LIST` / `AGGREGATE`）；大屏类优先大屏 Tool（「大屏推荐」+ 前置排序）。
- `AGGREGATE`：优先聚合 Tool；仅分页时单页 + coverage；禁止多页加总。
- 门禁：无筛选分页 / 错 Tool / 需先澄清 三类拒绝码。
- **兜底决策**：
  1. 分不清场景 / 缺槽 → `CLARIFY_ONLY`（`ai-clarify`）
  2. 场景清晰但无匹配 Tool、仍可改问 → `DEGRADE_CLARIFY`
  3. 无匹配 Tool 且无兜底 → `REFUSE`：不调模型硬编，直接回复拒绝原因与明细（Tool 计数等）
  4. 连续拒调 ≥2 → 停 Tool；能澄清则澄清，否则拒绝说明原因

```yaml
ysmp:
  ai:
    max-tool-store-chars: 512000
    tool-result-compact-threshold-rows: 20
    tool-result-sample-rows: 5
    tool-result-page-size-default: 10
    tool-result-ttl-ms: 1800000
```

提供 `AiSecurityAdapter` Bean（生产必做）。联调可临时：

```yaml
ysmp.ai.security.permit-all-when-missing: true
```

### 权限解析 SPI（可选）

默认在存在 Spring Security 时用 `PreAuthorizeEndpointPermissionResolver`。其它宿主可提供：

```java
@Bean
AiEndpointPermissionResolver myResolver() {
  return (method, path) -> List.of("biz:foo:query");
}
```

未提供且无 Security 时为 NoOp（`required_permissions` 为空，执行期仍靠宿主 API 鉴权）。

## 3. 前端

```ts
import { createAiAssistant } from '@ysmp/ai-assistant-core';
import { AiAssistantFloat, aiRouteViews } from '@ysmp/ai-assistant-ui';
import '@ysmp/ai-assistant-ui/style.css';

createAiAssistant({
  baseURL: `${import.meta.env.VITE_GLOB_API_URL}/admin-api`,
  getToken: () => accessStore.accessToken,
  hasPermission: (code) => hasAccessByCodes([code]),
});
```

对话输入：`@agent` / `@skill` / `@tool`（前缀匹配，忽略大小写）打开选择框。

流式对话：`POST {baseURL}/ai/assistant/chat/stream`（SSE）；同步仍可用 `/ai/assistant/chat`。core 提供 `chatStream()`。

## 4. 运维配置

1. 模型配置并启用  
2. OpenAPI 按模块勾选 Tool（注册时固化权限；可点「回填权限码」或重启）  
3. 配置 Skill（绑 Tool）与 Agent（`agent_mode` + 绑 Skill）  
4. 宿主角色勾选 `ai:assistant:query`、`ai:agent:{code}` 及业务 API 权限  

## 5. 权限码

- `ai:assistant:query` — 对话  
- `ai:model-config:*` / `ai:tool-api:*` / `ai:skill:*` / `ai:agent:*` — 配置 CRUD  
- `ai:agent:{code}` — 使用某 Agent  

## 6. 管理端页面

| component | 页面 |
|-----------|------|
| `ai/model-config` | 模型 |
| `ai/tool-api` | OpenAPI Tool + resync |
| `ai/skill` | 技能 |
| `ai/agent` | Agent（含 GENERAL/SCENARIO） |
