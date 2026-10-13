import type { AiRequestOptions, AiRuntimeAdapter, CreateAiAssistantOptions } from './types';

let runtime: AiRuntimeAdapter | null = null;

export function provideAiAssistant(adapter: AiRuntimeAdapter) {
  runtime = adapter;
}

export function useAiRuntime(): AiRuntimeAdapter {
  if (!runtime) {
    throw new Error('[ai-assistant] 请先调用 createAiAssistant / provideAiAssistant');
  }
  return runtime;
}

/**
 * 宿主初始化入口：注入 request / 权限 / Token。一次即可。
 */
export function createAiAssistant(options: CreateAiAssistantOptions): AiRuntimeAdapter {
  const baseURL = (options.baseURL || '').replace(/\/$/, '');
  const fetchImpl = options.fetchImpl || fetch;

  const adapter: AiRuntimeAdapter = {
    hasPermission: options.hasPermission || (() => true),
    getAccessToken: options.getAccessToken || (() => undefined),
    getBaseURL: options.getBaseURL || (() => baseURL || undefined),
    fetchImpl: fetchImpl,
    openBizDetail: options.openBizDetail,
    t: options.t,
    request:
      options.request ||
      (async <T>(opt: AiRequestOptions): Promise<T> => {
        const method = opt.method || 'GET';
        let url = opt.url.startsWith('http') ? opt.url : `${baseURL}${opt.url}`;
        if (opt.params) {
          const q = new URLSearchParams();
          Object.entries(opt.params).forEach(([k, v]) => {
            if (v !== undefined && v !== null) q.set(k, String(v));
          });
          url += (url.includes('?') ? '&' : '?') + q.toString();
        }
        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
        };
        const token = adapter.getAccessToken();
        if (token) {
          headers.Authorization = token.startsWith('Bearer') ? token : `Bearer ${token}`;
        }
        const res = await fetchImpl(url, {
          method,
          headers,
          body: opt.data !== undefined ? JSON.stringify(opt.data) : undefined,
        });
        const json = (await res.json()) as { code: number; msg: string; data: T };
        if (json.code !== 0) {
          throw new Error(json.msg || 'request failed');
        }
        return json.data;
      }),
  };

  provideAiAssistant(adapter);
  return adapter;
}
