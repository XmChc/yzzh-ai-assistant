/**
 * 与后端 cn.ysmp.ai.constants / AiConstants 对齐的全局常量。
 * 禁止在业务 UI 中内联权限码、模式、@ 关键词等魔法字符串。
 */

/** 门禁权限码（对齐 AiPermissionConstants） */
export const AiPermissions = {
  ASSISTANT_QUERY: 'ai:assistant:query',
  MODEL_CONFIG_QUERY: 'ai:model-config:query',
  MODEL_CONFIG_UPDATE: 'ai:model-config:update',
  TOOL_API_QUERY: 'ai:tool-api:query',
  TOOL_API_UPDATE: 'ai:tool-api:update',
  SKILL_QUERY: 'ai:skill:query',
  SKILL_UPDATE: 'ai:skill:update',
  AGENT_QUERY: 'ai:agent:query',
  AGENT_UPDATE: 'ai:agent:update',
  AGENT_DEFAULT: 'ai:agent:default',
  agentAccess: (agentCode: string) => `ai:agent:${agentCode}`,
} as const;

/** Agent 模式（对齐 AiAgentModes） */
export const AiAgentModes = {
  SCENARIO: 'SCENARIO',
  GENERAL: 'GENERAL',
} as const;

/** Tool 类型（对齐 AiToolTypes） */
export const AiToolTypes = {
  BUILTIN: 'BUILTIN',
  OPENAPI: 'OPENAPI',
  GOVERNED: 'GOVERNED',
} as const;

/** 渠道（对齐 AiChannels；本阶段配置页固定 ADMIN） */
export const AiChannels = {
  ADMIN: 'ADMIN',
  APP: 'APP',
} as const;

/** @ 引用关键词（对齐 AiMentionKeywords，前缀匹配忽略大小写） */
export const AiMentionKeywords = {
  AGENT: 'agent',
  SKILL: 'skill',
  TOOL: 'tool',
} as const;

/** 相对 admin-api 的 AI 路径片段（请求层已带 baseURL） */
export const AiApiRoutes = {
  ASSISTANT_CHAT: '/ai/assistant/chat',
  ASSISTANT_CHAT_STREAM: '/ai/assistant/chat/stream',
  ASSISTANT_CAPABILITIES: '/ai/assistant/capabilities',
  ASSISTANT_SESSIONS: '/ai/assistant/sessions',
  ASSISTANT_SESSION_MESSAGES: '/ai/assistant/session/messages',
  ASSISTANT_SESSION_DELETE: '/ai/assistant/session/delete',
  ASSISTANT_TOOL_RESULT_PAGE: '/ai/assistant/tool-result/page',
  ASSISTANT_TOOL_RESULT_FIELD: '/ai/assistant/tool-result/field',
  MODEL_CONFIG: '/ai/model-config',
  TOOL_API: '/ai/tool-api',
  SKILL: '/ai/skill',
  AGENT: '/ai/agent',
} as const;

/** SSE event 名（对齐 AiSseEventTypes） */
export const AiSseEventTypes = {
  META: 'meta',
  USAGE: 'usage',
  CLARIFY: 'clarify',
  TOOL_CALL: 'tool_call',
  TOOL_RESULT: 'tool_result',
  TOOL_DATA: 'tool_data',
  TOOL_CONFIRM: 'tool_confirm',
  DELTA: 'delta',
  DONE: 'done',
  ERROR: 'error',
} as const;

/** 写操作确认协议（对齐 AiWriteConfirmConstants） */
export const AiWriteConfirm = {
  STATUS_NEED_CONFIRM: 'NEED_USER_CONFIRM',
  FIELD_STATUS: 'status',
  FIELD_TOOL: 'tool',
  FIELD_MESSAGE: 'message',
} as const;

/** 澄清补充协议（对齐 AiClarifyConstants） */
export const AiClarify = {
  FENCE_LANG: 'ai-clarify',
  REPLY_PREFIX: '【补充说明】',
  FIELD_TEXT: 'text',
  FIELD_TEXTAREA: 'textarea',
  FIELD_NUMBER: 'number',
  FIELD_SELECT: 'select',
  FIELD_SINGLE: 'single',
  FIELD_MULTI: 'multi',
  MAX_FIELDS: 8,
} as const;

/** 内置图表协议（对齐 AiChartConstants） */
export const AiChart = {
  FENCE_LANG: 'ai-chart',
  TYPE_BAR: 'bar',
  TYPE_HORIZONTAL_BAR: 'horizontal_bar',
  TYPE_LINE: 'line',
  TYPE_AREA: 'area',
  TYPE_STACKED_BAR: 'stacked_bar',
  TYPE_PIE: 'pie',
  TYPE_DONUT: 'donut',
  TYPE_SCATTER: 'scatter',
  TYPE_RADAR: 'radar',
  TYPE_FUNNEL: 'funnel',
  MAX_CHARTS_PER_REPLY: 6,
} as const;

/** 查询意图（对齐 AiQueryIntents） */
export const AiQueryIntents = {
  LIST: 'LIST',
  AGGREGATE: 'AGGREGATE',
  LOOKUP: 'LOOKUP',
  STATUS: 'STATUS',
  TREND: 'TREND',
  COMPARE: 'COMPARE',
  RANK: 'RANK',
  DISTRIBUTION: 'DISTRIBUTION',
  EXPORT: 'EXPORT',
  EXPLAIN: 'EXPLAIN',
  ACTION: 'ACTION',
  META: 'META',
  CLARIFY_NEEDED: 'CLARIFY_NEEDED',
  UNKNOWN: 'UNKNOWN',
  /** 大屏参数类 */
  DASHBOARD: ['TREND', 'COMPARE', 'RANK', 'DISTRIBUTION'] as const,
} as const;

/** 数值上限（对齐 AiLimits） */
export const AiLimits = {
  MAX_INACTIVE_SKILL_HINTS: 30,
  MAX_TOOL_ROUNDS: 8,
  SSE_CONTENT_CHUNK_CHARS: 48,
  TOOL_RESULT_PREVIEW_CHARS: 500,
  MAX_CONTEXT_MESSAGES: 40,
  MAX_CONTEXT_CHARS: 48000,
  DEFAULT_CONTEXT_WINDOW_TOKENS: 128000,
  DEFAULT_SESSION_LIST_LIMIT: 50,
  SUMMARY_KEEP_RECENT_MESSAGES: 12,
  SUMMARY_TRIGGER_MESSAGES: 20,
  MAX_SUMMARY_CHARS: 2500,
  TOOL_RESULT_STATS_MAX_FIELDS: 20,
  TOOL_RESULT_STATS_MAX_SCAN_ROWS: 20000,
  TOOL_RESULT_STATS_TOP_K: 8,
  TOOL_RESULT_STATS_MAX_DISTINCT: 64,
  MAX_TOOL_RETURNS_SCHEMA_CHARS: 1200,
  MAX_OPENAPI_SCHEMA_DEPTH: 8,
  TOOL_QUERY_MAX_PAGE_SIZE: 100,
} as const;
