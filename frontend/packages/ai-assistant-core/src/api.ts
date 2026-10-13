import { AiApiRoutes, AiSseEventTypes } from './constants';
import type {
  AiChatMessageRow,
  AiChatRequest,
  AiChatResponse,
  AiChatSessionSummary,
  AiChatStreamHandlers,
  AiClarifyForm,
  AiContextUsage,
  AiToolDataRef,
  AiToolResultPage,
} from './types';
import { useAiRuntime } from './runtime';

export async function chat(req: AiChatRequest): Promise<AiChatResponse> {
  return useAiRuntime().request<AiChatResponse>({
    method: 'POST',
    url: AiApiRoutes.ASSISTANT_CHAT,
    data: req,
  });
}

/**
 * SSE 流式对话（tool_call / delta / done）。
 */
export async function chatStream(
  req: AiChatRequest,
  handlers: AiChatStreamHandlers = {},
): Promise<void> {
  const rt = useAiRuntime();
  const base = (rt.getBaseURL?.() || '').replace(/\/$/, '');
  const fetchImpl = rt.fetchImpl || fetch;
  const url = `${base}${AiApiRoutes.ASSISTANT_CHAT_STREAM}`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  };
  const token = rt.getAccessToken();
  if (token) {
    headers.Authorization = token.startsWith('Bearer') ? token : `Bearer ${token}`;
  }

  const res = await fetchImpl(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(req),
  });
  if (!res.ok || !res.body) {
    throw new Error(`stream failed: HTTP ${res.status}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let currentEvent = '';

  const dispatch = (event: string, dataRaw: string) => {
    let data: Record<string, unknown> = {};
    try {
      data = JSON.parse(dataRaw) as Record<string, unknown>;
    } catch {
      data = { raw: dataRaw };
    }
    switch (event) {
      case AiSseEventTypes.META:
        handlers.onMeta?.(String(data.sessionId ?? ''));
        break;
      case AiSseEventTypes.USAGE:
        handlers.onUsage?.(data as unknown as AiContextUsage);
        break;
      case AiSseEventTypes.CLARIFY:
        handlers.onClarify?.(data as unknown as AiClarifyForm);
        break;
      case AiSseEventTypes.TOOL_CALL:
        handlers.onToolCall?.({
          id: String(data.id ?? ''),
          name: String(data.name ?? ''),
          arguments: data.arguments != null ? String(data.arguments) : undefined,
        });
        break;
      case AiSseEventTypes.TOOL_RESULT:
        handlers.onToolResult?.({
          id: String(data.id ?? ''),
          name: String(data.name ?? ''),
          preview: data.preview != null ? String(data.preview) : undefined,
        });
        break;
      case AiSseEventTypes.TOOL_DATA:
        handlers.onToolData?.(data as unknown as AiToolDataRef);
        break;
      case AiSseEventTypes.DELTA:
        handlers.onDelta?.(String(data.text ?? ''));
        break;
      case AiSseEventTypes.DONE:
        handlers.onDone?.(String(data.content ?? ''));
        break;
      case AiSseEventTypes.ERROR:
        handlers.onError?.(String(data.message ?? 'stream error'));
        break;
      default:
        break;
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split(/\n\n/);
    buffer = parts.pop() || '';
    for (const block of parts) {
      const lines = block.split(/\n/);
      let dataLines: string[] = [];
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim());
        }
      }
      if (dataLines.length) {
        dispatch(currentEvent || 'message', dataLines.join('\n'));
        currentEvent = '';
      }
    }
  }
}

export async function getModelConfig<T = unknown>(): Promise<T> {
  return useAiRuntime().request<T>({ method: 'GET', url: `${AiApiRoutes.MODEL_CONFIG}/get` });
}

export async function updateModelConfig(data: unknown): Promise<boolean> {
  return useAiRuntime().request<boolean>({
    method: 'PUT',
    url: `${AiApiRoutes.MODEL_CONFIG}/update`,
    data,
  });
}

export async function listToolApis<T = unknown>(): Promise<T> {
  return useAiRuntime().request<T>({ method: 'GET', url: `${AiApiRoutes.TOOL_API}/list` });
}

export async function listOpenApiCatalog<T = unknown>(): Promise<T> {
  return useAiRuntime().request<T>({
    method: 'GET',
    url: `${AiApiRoutes.TOOL_API}/openapi-catalog`,
  });
}

export async function registerOpenApiTool(data: {
  method: string;
  path: string;
  summary?: string;
  operationId?: string;
  /** 功能模块（OpenAPI tag / 路径首段） */
  module?: string;
}): Promise<boolean> {
  return useAiRuntime().request<boolean>({
    method: 'POST',
    url: `${AiApiRoutes.TOOL_API}/register-openapi`,
    data,
  });
}

export async function listSkills<T = unknown>(): Promise<T> {
  return useAiRuntime().request<T>({ method: 'GET', url: `${AiApiRoutes.SKILL}/list` });
}

export async function getSkill<T = unknown>(id: number): Promise<T> {
  return useAiRuntime().request<T>({
    method: 'GET',
    url: `${AiApiRoutes.SKILL}/get`,
    params: { id },
  });
}

export async function createSkill(data: unknown): Promise<number> {
  return useAiRuntime().request<number>({
    method: 'POST',
    url: `${AiApiRoutes.SKILL}/create`,
    data,
  });
}

export async function updateSkill(data: unknown): Promise<boolean> {
  return useAiRuntime().request<boolean>({
    method: 'PUT',
    url: `${AiApiRoutes.SKILL}/update`,
    data,
  });
}

export async function listAgents<T = unknown>(): Promise<T> {
  return useAiRuntime().request<T>({ method: 'GET', url: `${AiApiRoutes.AGENT}/list` });
}

export async function listAvailableAgents<T = unknown>(): Promise<T> {
  return useAiRuntime().request<T>({ method: 'GET', url: `${AiApiRoutes.AGENT}/available` });
}

export async function getAssistantCapabilities<T = unknown>(agentCode?: string): Promise<T> {
  return useAiRuntime().request<T>({
    method: 'GET',
    url: AiApiRoutes.ASSISTANT_CAPABILITIES,
    params: agentCode ? { agentCode } : undefined,
  });
}

export async function listChatSessions(limit?: number): Promise<AiChatSessionSummary[]> {
  return useAiRuntime().request<AiChatSessionSummary[]>({
    method: 'GET',
    url: AiApiRoutes.ASSISTANT_SESSIONS,
    params: limit != null ? { limit } : undefined,
  });
}

export async function listChatMessages(sessionId: string): Promise<AiChatMessageRow[]> {
  return useAiRuntime().request<AiChatMessageRow[]>({
    method: 'GET',
    url: AiApiRoutes.ASSISTANT_SESSION_MESSAGES,
    params: { sessionId },
  });
}

export async function deleteChatSession(sessionId: string): Promise<boolean> {
  return useAiRuntime().request<boolean>({
    method: 'POST',
    url: AiApiRoutes.ASSISTANT_SESSION_DELETE,
    params: { sessionId },
  });
}

export async function getToolResultPage(params: {
  refId: string;
  page?: number;
  pageSize?: number;
}): Promise<AiToolResultPage> {
  return useAiRuntime().request<AiToolResultPage>({
    method: 'GET',
    url: AiApiRoutes.ASSISTANT_TOOL_RESULT_PAGE,
    params,
  });
}

export async function getToolResultField(params: {
  refId: string;
  rowIndex: number;
  field: string;
  page?: number;
  pageSize?: number;
}): Promise<AiToolResultPage> {
  return useAiRuntime().request<AiToolResultPage>({
    method: 'GET',
    url: AiApiRoutes.ASSISTANT_TOOL_RESULT_FIELD,
    params,
  });
}

export async function resyncToolPermissions(): Promise<number> {
  return useAiRuntime().request<number>({
    method: 'POST',
    url: `${AiApiRoutes.TOOL_API}/resync-permissions`,
  });
}

export async function resyncToolSchemas(): Promise<number> {
  return useAiRuntime().request<number>({
    method: 'POST',
    url: `${AiApiRoutes.TOOL_API}/resync-schemas`,
  });
}

export async function getAgent<T = unknown>(id: number): Promise<T> {
  return useAiRuntime().request<T>({
    method: 'GET',
    url: `${AiApiRoutes.AGENT}/get`,
    params: { id },
  });
}

export async function createAgent(data: unknown): Promise<number> {
  return useAiRuntime().request<number>({
    method: 'POST',
    url: `${AiApiRoutes.AGENT}/create`,
    data,
  });
}

export async function updateAgent(data: unknown): Promise<boolean> {
  return useAiRuntime().request<boolean>({
    method: 'PUT',
    url: `${AiApiRoutes.AGENT}/update`,
    data,
  });
}
