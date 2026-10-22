/** 助手配置中心 Tab 键 */
export type AiConfigTab = 'model' | 'tool' | 'skill' | 'agent';

export const AI_CONFIG_TABS: Array<{
  key: AiConfigTab;
  label: string;
}> = [
  { key: 'model', label: '模型' },
  { key: 'tool', label: '接口' },
  { key: 'skill', label: '技能' },
  { key: 'agent', label: '智能体' },
];

export function isAiConfigTab(v: unknown): v is AiConfigTab {
  return v === 'model' || v === 'tool' || v === 'skill' || v === 'agent';
}

export function asBool(v: unknown): boolean {
  if (v === true || v === 1) return true;
  if (typeof v === 'string') return v === '1' || v.toLowerCase() === 'true';
  return false;
}

export function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}
