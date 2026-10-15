import { AiChart } from './constants';
import type { AiChartSpec } from './types';

export type { AiChartSpec } from './types';

export type AiContentSegment =
  | { kind: 'markdown'; text: string }
  | { kind: 'chart'; chart: AiChartSpec };

const FENCE_RE = new RegExp(
  '```\\s*' + AiChart.FENCE_LANG + '\\s*\\n([\\s\\S]*?)```',
  'gi',
);

/** 将助手正文拆成 markdown 段与图表段（保留图表在原文中的位置）。 */
export function splitContentWithCharts(content: string): AiContentSegment[] {
  if (!content) return [];
  const segments: AiContentSegment[] = [];
  let last = 0;
  let count = 0;
  const re = new RegExp(FENCE_RE.source, 'gi');
  let m: RegExpExecArray | null;
  while ((m = re.exec(content)) !== null) {
    if (m.index > last) {
      const text = content.slice(last, m.index).trim();
      if (text) segments.push({ kind: 'markdown', text });
    }
    if (count < AiChart.MAX_CHARTS_PER_REPLY) {
      const chart = parseChartJson(m[1] || '');
      if (chart) {
        segments.push({ kind: 'chart', chart });
        count += 1;
      } else {
        segments.push({ kind: 'markdown', text: m[0] });
      }
    }
    last = m.index + m[0].length;
  }
  if (last < content.length) {
    const text = content.slice(last).trim();
    if (text) segments.push({ kind: 'markdown', text });
  }
  return segments.length ? segments : [{ kind: 'markdown', text: content }];
}

export function parseChartJson(raw: string): AiChartSpec | null {
  try {
    const obj = JSON.parse(raw.trim()) as AiChartSpec;
    if (!obj || typeof obj !== 'object' || !obj.type) return null;
    const type = String(obj.type).toLowerCase().trim();
    if (!Object.values(AiChart).includes(type as never) && !isChartType(type)) {
      return null;
    }
    return { ...obj, type };
  } catch {
    return null;
  }
}

function isChartType(t: string): boolean {
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
