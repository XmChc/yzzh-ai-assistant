# Agent 规范入口

本工作空间的 Agent 编程约束以根目录 **[AGENTS.md](./AGENTS.md)** 为准（Cursor 标准文件名）。

补充规则：`.cursor/rules/constants-and-guard.mdc`（始终应用）。

核心要求：业务代码统一使用 `cn.ysmp.ai.constants` / `cn.ysmp.ai.guard` 与前端 `constants.ts`，禁止魔法字符串。
