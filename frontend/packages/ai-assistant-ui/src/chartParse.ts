import { AiChart, type AiChartSpec } from '@ysmp/ai-assistant-core';

export type ContentSegment =
  | { kind: 'md'; text: string }
  | { kind: 'chart'; chart: AiChartSpec };

const FENCE_RE = new RegExp(
  '```\\s*' + AiChart.FENCE_LANG + '\\s*\\n([\\s\\S]*?)```',
  'gi',
);

/**
 * 将助手正文拆成 Markdown 段与图表段（保留出现顺序）。
 */
export function splitContentWithCharts(src: string): ContentSegment[] {
  if (!src) return [];
  const segments: ContentSegment[] = [];
  let last = 0;
  const re = new RegExp(FENCE_RE.source, 'gi');
  let m: RegExpExecArray | null;
  let count = 0;
  while ((m = re.exec(src)) !== null) {
    if (m.index > last) {
      const text = src.slice(last, m.index).trim();
      if (text) segments.push({ kind: 'md', text });
    }
    if (count < AiChart.MAX_CHARTS_PER_REPLY) {
      const chart = parseChartJson(m[1] || '');
      if (chart) {
        segments.push({ kind: 'chart', chart });
        count += 1;
      }
    }
    last = m.index + m[0].length;
  }
  if (last < src.length) {
    const text = src.slice(last).trim();
    if (text) segments.push({ kind: 'md', text });
  }
  if (!segments.length && src.trim()) {
    segments.push({ kind: 'md', text: src });
  }
  return segments;
}

function parseChartJson(raw: string): AiChartSpec | null {
  try {
    const obj = JSON.parse(raw.trim()) as AiChartSpec;
    if (!obj || typeof obj !== 'object' || !obj.type) return null;
    const type = String(obj.type).toLowerCase().trim();
    if (!isKnownType(type)) return null;
    return { ...obj, type };
  } catch {
    return null;
  }
}

function isKnownType(t: string): boolean {
  return (
    t === AiChart.TYPE_BAR ||
    t === AiChart.TYPE_HORIZONTAL_BAR ||
    t === AiChart.TYPE_LINE ||
    t === AiChart.TYPE_AREA ||
    t === AiChart.TYPE_STACKED_BAR ||
    t === AiChart.TYPE_PIE ||
    t === AiChart.TYPE_DONUT ||
    t === AiChart.TYPE_SCATTER ||
    t === AiChart.TYPE_RADAR ||
    t === AiChart.TYPE_FUNNEL
  );
}
