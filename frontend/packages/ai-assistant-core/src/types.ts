export interface AiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  url: string;
  params?: Record<string, unknown>;
  data?: unknown;
}

export interface AiRuntimeAdapter {
  request: <T>(opt: AiRequestOptions) => Promise<T>;
  hasPermission: (code: string) => boolean;
  getAccessToken: () => string | undefined;
  /** 如 /admin-api，供 SSE 等直连 fetch */
  getBaseURL?: () => string | undefined;
  fetchImpl?: typeof fetch;
  openBizDetail?: (ref: { bizType: string; bizId: string }) => void;
  t?: (key: string, params?: Record<string, unknown>) => string;
}

export interface AiContextUsage {
  estimatedTotal: number;
  contextWindow: number;
  percentFull: number;
  systemPrompt: number;
  skillPrompts: number;
  toolDefinitions: number;
  /** 滚动摘要（早期轮次压缩） */
  summarizedConversation: number;
  conversation: number;
  currentMessage: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
}

export interface AiClarifyOption {
  value: string;
  label: string;
}

export interface AiClarifyField {
  id: string;
  label: string;
  type: string;
  required?: boolean;
  placeholder?: string;
  defaultValue?: string;
  options?: AiClarifyOption[];
}

export interface AiClarifyForm {
  id: string;
  title?: string;
  description?: string;
  fields: AiClarifyField[];
}

export interface AiToolDataRef {
  refId: string;
  toolCode?: string;
  shape?: string;
  fields?: string[];
  total?: number;
  reportedTotal?: number;
  pageSize?: number;
  sampleRows?: Record<string, unknown>[];
  /** 列级汇总：数值 min/max/sum/avg，分类 top */
  stats?: Record<string, unknown>;
  compacted?: boolean;
}

export interface AiToolResultPage {
  refId: string;
  toolCode?: string;
  shape?: string;
  fields?: string[];
  rows?: Record<string, unknown>[];
  page: number;
  pageSize: number;
  total: number;
  reportedTotal?: number;
  parentField?: string;
}

export interface AiChatStreamHandlers {
  onMeta?: (sessionId: string) => void;
  onUsage?: (usage: AiContextUsage) => void;
  onClarify?: (form: AiClarifyForm) => void;
  onToolCall?: (p: { id: string; name: string; arguments?: string }) => void;
  onToolResult?: (p: { id: string; name: string; preview?: string }) => void;
  onToolData?: (data: AiToolDataRef) => void;
  onDelta?: (text: string) => void;
  onDone?: (content: string) => void;
  onError?: (message: string) => void;
}

export interface CreateAiAssistantOptions extends Partial<AiRuntimeAdapter> {
  /** 如 /admin-api；若提供完整 request 则可省略 */
  baseURL?: string;
  fetchImpl?: typeof fetch;
}

export interface AiChatRequest {
  sessionId?: string;
  agentCode?: string;
  skillCode?: string;
  toolCode?: string;
  message: string;
}

export interface AiChatResponse {
  sessionId?: string;
  content?: string;
  error?: string;
  usage?: AiContextUsage;
  clarify?: AiClarifyForm;
}

export interface AiChatSessionSummary {
  sessionId: string;
  userId?: number;
  agentCode?: string;
  title?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AiChatMessageRow {
  id?: number;
  role: string;
  content?: string;
  toolSummary?: string;
  createTime?: string;
}

/** 内置图表（ai-chart） */
export interface AiChartSeries {
  name: string;
  data?: number[];
  points?: number[][];
}

export interface AiChartIndicator {
  name: string;
  max: number;
}

export interface AiChartSlice {
  name: string;
  value: number;
}

export interface AiChartSpec {
  type: string;
  title?: string;
  unit?: string;
  categories?: string[];
  series?: AiChartSeries[];
  data?: AiChartSlice[];
  smooth?: boolean;
  xLabel?: string;
  yLabel?: string;
  indicators?: AiChartIndicator[];
}

export interface AiSkillHint {
  skillCode: string;
  name?: string;
  systemPrompt?: string;
  activated?: boolean;
  missingHints?: string[];
  toolCodes?: string[];
}

export interface AiToolDefinition {
  toolCode: string;
  name?: string;
  description?: string;
  available?: boolean;
}

export interface AiCapabilityBundle {
  agentCode?: string;
  agentMode?: string;
  agentSystemPrompt?: string;
  agentAccessible?: boolean;
  error?: string;
  callableTools?: AiToolDefinition[];
  activatedSkills?: AiSkillHint[];
  inactiveSkills?: AiSkillHint[];
}

export interface AiCommonResult<T> {
  code: number;
  msg: string;
  data: T;
}
