import { AiClarify } from './constants';
import type { AiClarifyField, AiClarifyForm } from './types';

/** 将表单填写结果格式化为用户消息正文 */
export function formatClarifyReply(
  form: AiClarifyForm,
  values: Record<string, string | string[]>,
): string {
  const lines: string[] = [AiClarify.REPLY_PREFIX];
  if (form.title) {
    lines.push(`（${form.title}）`);
  }
  for (const field of form.fields || []) {
    const raw = values[field.id];
    const text = formatFieldValue(field, raw);
    if (!text) continue;
    lines.push(`- ${field.label}: ${text}`);
  }
  return lines.join('\n');
}

function formatFieldValue(field: AiClarifyField, raw: string | string[] | undefined): string {
  if (raw == null) return '';
  if (Array.isArray(raw)) {
    if (!raw.length) return '';
    const labels = raw.map((v) => optionLabel(field, v));
    return labels.join('、');
  }
  const s = String(raw).trim();
  if (!s) return '';
  return optionLabel(field, s);
}

function optionLabel(field: AiClarifyField, value: string): string {
  const hit = (field.options || []).find((o) => o.value === value);
  return hit?.label || value;
}

export function validateClarify(
  form: AiClarifyForm,
  values: Record<string, string | string[]>,
): string | null {
  for (const field of form.fields || []) {
    if (!field.required) continue;
    const v = values[field.id];
    if (Array.isArray(v)) {
      if (!v.length) return `请填写：${field.label}`;
    } else if (v == null || String(v).trim() === '') {
      return `请填写：${field.label}`;
    }
  }
  return null;
}
