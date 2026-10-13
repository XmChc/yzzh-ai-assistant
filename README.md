# yzzh-ai-assistant

可嵌入管理端的 AI 问答助手 **SDK**（后端 Maven + 前端 npm）。

宿主 **依赖并初始化** 后，在管理端配置模型、按模块勾选并启用 OpenAPI Tool（含 POST 分页查询）、配置 Skill/Agent 即可使用，无需为业务再写 Tool 代码。

## 目录

```
backend/
  ysmp-spring-boot-starter-ai/   # 引擎（SPI / 编排 / LLM / OpenAPI 执行）
  ysmp-module-ai-boot/           # 开箱（表 / 管理 API / Skill·Agent）
frontend/
  packages/ai-assistant-core/    # createAiAssistant + API + 权限常量
  packages/ai-assistant-ui/      # 默认 UI（CSS 变量换肤）
docs/
  architecture.md                # 架构说明
  integration.md                 # 接入步骤
```

## 架构一览

见 [docs/architecture.md](docs/architecture.md)。

```text
Agent(SCENARIO|GENERAL) → Skill 激活(全部 Tool available) → Tool(OpenAPI)
对话可用 @agent / @skill / @tool 引用；Tool 权限 SPI 固化到 DB
```

## 构建

```bash
# JDK 17+
cd backend && mvn clean install -DskipTests

cd frontend && pnpm install && pnpm --filter @ysmp/ai-assistant-core build
```

## 接入摘要

1. 依赖 `ysmp-module-ai-boot`，执行 `sql/ai_schema.sql`  
2. 配置：

```yaml
ysmp:
  ai:
    enabled: true
    internal-base-url: http://127.0.0.1:58080
    # 本地无适配器时才打开：
    # security.permit-all-when-missing: true
```

3. 提供 `AiSecurityAdapter`（生产必做）  
4. 前端 `createAiAssistant({ baseURL, getToken, hasPermission })` + 挂载 UI  

完整步骤：[docs/integration.md](docs/integration.md)

## 版本

`0.1.0-SNAPSHOT`（孵化中，默认本地 `mvn install` / pnpm workspace）

## Agent 规范

见 [AGENTS.md](AGENTS.md)（入口 [agent.md](agent.md)）：常量与门禁统一、模块边界、禁止魔法字符串。
