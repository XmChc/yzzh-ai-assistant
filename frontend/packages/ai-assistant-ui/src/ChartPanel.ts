import { AiChart, type AiChartSpec } from '@ysmp/ai-assistant-core';
import { h, type VNode } from 'vue';

const COLORS = [
  '#1677ff',
  '#52c41a',
  '#faad14',
  '#eb2f96',
  '#13c2c2',
  '#722ed1',
  '#fa541c',
  '#2f54eb',
];

export function renderChart(chart: AiChartSpec): VNode {
  const type = chart.type;
  let body: VNode;
  try {
    if (type === AiChart.TYPE_PIE || type === AiChart.TYPE_DONUT) {
      body = renderPie(chart, type === AiChart.TYPE_DONUT);
    } else if (type === AiChart.TYPE_FUNNEL) {
      body = renderFunnel(chart);
    } else if (type === AiChart.TYPE_SCATTER) {
      body = renderScatter(chart);
    } else if (type === AiChart.TYPE_RADAR) {
      body = renderRadar(chart);
    } else if (type === AiChart.TYPE_HORIZONTAL_BAR) {
      body = renderBars(chart, true, false);
    } else if (type === AiChart.TYPE_STACKED_BAR) {
      body = renderBars(chart, false, true);
    } else if (type === AiChart.TYPE_LINE || type === AiChart.TYPE_AREA) {
      body = renderLine(chart, type === AiChart.TYPE_AREA);
    } else {
      body = renderBars(chart, false, false);
    }
  } catch {
    body = h('div', { class: 'ai-chart-error' }, '图表数据无法渲染');
  }
  return h('div', { class: 'ai-chart' }, [
    chart.title ? h('div', { class: 'ai-chart-title' }, chart.title) : null,
    body,
    chart.unit ? h('div', { class: 'ai-chart-unit' }, `单位：${chart.unit}`) : null,
    renderLegend(chart),
  ]);
}

function renderLegend(chart: AiChartSpec): VNode | null {
  if (chart.data?.length) {
    return h(
      'div',
      { class: 'ai-chart-legend' },
      chart.data.map((d, i) =>
        h('span', { class: 'ai-chart-legend-item' }, [
          h('i', { style: { background: COLORS[i % COLORS.length] } }),
          `${d.name}（${d.value}）`,
        ]),
      ),
    );
  }
  if (chart.series?.length) {
    return h(
      'div',
      { class: 'ai-chart-legend' },
      chart.series.map((s, i) =>
        h('span', { class: 'ai-chart-legend-item' }, [
          h('i', { style: { background: COLORS[i % COLORS.length] } }),
          s.name,
        ]),
      ),
    );
  }
  return null;
}

function renderBars(chart: AiChartSpec, horizontal: boolean, stacked: boolean): VNode {
  const cats = chart.categories || [];
  const series = chart.series || [];
  const w = 360;
  const hgt = horizontal ? Math.max(160, cats.length * 28 + 40) : 200;
  const pad = { t: 16, r: 16, b: horizontal ? 24 : 36, l: horizontal ? 72 : 36 };
  const iw = w - pad.l - pad.r;
  const ih = hgt - pad.t - pad.b;

  const allVals: number[] = [];
  if (stacked) {
    for (let i = 0; i < cats.length; i++) {
      let sum = 0;
      for (const s of series) sum += num(s.data?.[i]);
      allVals.push(sum);
    }
  } else {
    for (const s of series) for (const v of s.data || []) allVals.push(num(v));
  }
  const maxV = Math.max(1, ...allVals);

  const nodes: VNode[] = [];
  // axes
  nodes.push(
    h('line', {
      x1: pad.l,
      y1: pad.t,
      x2: pad.l,
      y2: pad.t + ih,
      class: 'ai-chart-axis',
    }),
  );
  nodes.push(
    h('line', {
      x1: pad.l,
      y1: pad.t + ih,
      x2: pad.l + iw,
      y2: pad.t + ih,
      class: 'ai-chart-axis',
    }),
  );

  if (horizontal) {
    const rowH = ih / Math.max(1, cats.length);
    cats.forEach((cat, i) => {
      const y = pad.t + i * rowH + rowH * 0.15;
      const bh = rowH * 0.7;
      nodes.push(
        h(
          'text',
          {
            x: pad.l - 6,
            y: y + bh / 2,
            'text-anchor': 'end',
            'dominant-baseline': 'middle',
            class: 'ai-chart-label',
          },
          truncate(cat, 8),
        ),
      );
      if (stacked) {
        let x = pad.l;
        series.forEach((s, si) => {
          const v = num(s.data?.[i]);
          const bw = (v / maxV) * iw;
          nodes.push(
            h('rect', {
              x,
              y,
              width: Math.max(0, bw),
              height: bh,
              fill: COLORS[si % COLORS.length],
              rx: 2,
            }),
          );
          x += bw;
        });
      } else {
        const gap = bh / Math.max(1, series.length);
        series.forEach((s, si) => {
          const v = num(s.data?.[i]);
          const bw = (v / maxV) * iw;
          nodes.push(
            h('rect', {
              x: pad.l,
              y: y + si * gap,
              width: Math.max(0, bw),
              height: Math.max(2, gap * 0.85),
              fill: COLORS[si % COLORS.length],
              rx: 2,
            }),
          );
        });
      }
    });
  } else {
    const groupW = iw / Math.max(1, cats.length);
    cats.forEach((cat, i) => {
      const cx = pad.l + i * groupW + groupW / 2;
      nodes.push(
        h(
          'text',
          {
            x: cx,
            y: pad.t + ih + 14,
            'text-anchor': 'middle',
            class: 'ai-chart-label',
          },
          truncate(cat, 6),
        ),
      );
      if (stacked) {
        let y = pad.t + ih;
        series.forEach((s, si) => {
          const v = num(s.data?.[i]);
          const bh = (v / maxV) * ih;
          y -= bh;
          nodes.push(
            h('rect', {
              x: cx - groupW * 0.3,
              y,
              width: groupW * 0.6,
              height: Math.max(0, bh),
              fill: COLORS[si % COLORS.length],
              rx: 2,
            }),
          );
        });
      } else {
        const barW = (groupW * 0.7) / Math.max(1, series.length);
        series.forEach((s, si) => {
          const v = num(s.data?.[i]);
          const bh = (v / maxV) * ih;
          const x = pad.l + i * groupW + groupW * 0.15 + si * barW;
          nodes.push(
            h('rect', {
              x,
              y: pad.t + ih - bh,
              width: Math.max(2, barW * 0.9),
              height: Math.max(0, bh),
              fill: COLORS[si % COLORS.length],
              rx: 2,
            }),
          );
        });
      }
    });
  }

  return h('svg', { class: 'ai-chart-svg', viewBox: `0 0 ${w} ${hgt}`, width: '100%' }, nodes);
}

function renderLine(chart: AiChartSpec, area: boolean): VNode {
  const cats = chart.categories || [];
  const series = chart.series || [];
  const w = 360;
  const hgt = 200;
  const pad = { t: 16, r: 16, b: 36, l: 36 };
  const iw = w - pad.l - pad.r;
  const ih = hgt - pad.t - pad.b;
  const allVals: number[] = [];
  for (const s of series) for (const v of s.data || []) allVals.push(num(v));
  const maxV = Math.max(1, ...allVals);
  const n = Math.max(1, cats.length);

  const nodes: VNode[] = [
    h('line', {
      x1: pad.l,
      y1: pad.t,
      x2: pad.l,
      y2: pad.t + ih,
      class: 'ai-chart-axis',
    }),
    h('line', {
      x1: pad.l,
      y1: pad.t + ih,
      x2: pad.l + iw,
      y2: pad.t + ih,
      class: 'ai-chart-axis',
    }),
  ];

  cats.forEach((cat, i) => {
    const x = pad.l + (i / Math.max(1, n - 1)) * iw;
    nodes.push(
      h(
        'text',
        {
          x,
          y: pad.t + ih + 14,
          'text-anchor': 'middle',
          class: 'ai-chart-label',
        },
        truncate(cat, 6),
      ),
    );
  });

  series.forEach((s, si) => {
    const pts = (s.data || []).map((v, i) => {
      const x = pad.l + (i / Math.max(1, n - 1)) * iw;
      const y = pad.t + ih - (num(v) / maxV) * ih;
      return [x, y] as [number, number];
    });
    if (!pts.length) return;
    const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0]},${p[1]}`).join(' ');
    if (area) {
      const base = pad.t + ih;
      const areaPath =
        line +
        ` L${pts[pts.length - 1][0]},${base} L${pts[0][0]},${base} Z`;
      nodes.push(
        h('path', {
          d: areaPath,
          fill: COLORS[si % COLORS.length],
          'fill-opacity': 0.2,
          stroke: 'none',
        }),
      );
    }
    nodes.push(
      h('path', {
        d: line,
        fill: 'none',
        stroke: COLORS[si % COLORS.length],
        'stroke-width': 2,
      }),
    );
    pts.forEach(([x, y]) => {
      nodes.push(
        h('circle', {
          cx: x,
          cy: y,
          r: 3,
          fill: COLORS[si % COLORS.length],
        }),
      );
    });
  });

  return h('svg', { class: 'ai-chart-svg', viewBox: `0 0 ${w} ${hgt}`, width: '100%' }, nodes);
}

function renderPie(chart: AiChartSpec, donut: boolean): VNode {
  const data = (chart.data || []).filter((d) => num(d.value) > 0);
  const total = data.reduce((s, d) => s + num(d.value), 0) || 1;
  const w = 280;
  const hgt = 200;
  const cx = w / 2;
  const cy = hgt / 2;
  const r = 72;
  const ir = donut ? 40 : 0;
  let angle = -Math.PI / 2;
  const nodes: VNode[] = [];

  data.forEach((d, i) => {
    const slice = (num(d.value) / total) * Math.PI * 2;
    const a0 = angle;
    const a1 = angle + slice;
    angle = a1;
    const path = donut
      ? donutSlice(cx, cy, r, ir, a0, a1)
      : pieSlice(cx, cy, r, a0, a1);
    nodes.push(h('path', { d: path, fill: COLORS[i % COLORS.length] }));
  });

  if (donut) {
    nodes.push(
      h('circle', {
        cx,
        cy,
        r: ir - 1,
        fill: '#f7f8fa',
      }),
    );
  }

  return h('svg', { class: 'ai-chart-svg', viewBox: `0 0 ${w} ${hgt}`, width: '100%' }, nodes);
}

function renderFunnel(chart: AiChartSpec): VNode {
  const data = chart.data || [];
  const w = 320;
  const rowH = 36;
  const hgt = Math.max(120, data.length * rowH + 20);
  const maxV = Math.max(1, ...data.map((d) => num(d.value)));
  const nodes: VNode[] = [];
  data.forEach((d, i) => {
    const ratio = num(d.value) / maxV;
    const bw = Math.max(40, ratio * (w - 40));
    const x = (w - bw) / 2;
    const y = 10 + i * rowH;
    nodes.push(
      h('rect', {
        x,
        y,
        width: bw,
        height: rowH - 8,
        fill: COLORS[i % COLORS.length],
        rx: 4,
      }),
    );
    nodes.push(
      h(
        'text',
        {
          x: w / 2,
          y: y + (rowH - 8) / 2,
          'text-anchor': 'middle',
          'dominant-baseline': 'middle',
          class: 'ai-chart-funnel-label',
        },
        `${d.name} ${d.value}`,
      ),
    );
  });
  return h('svg', { class: 'ai-chart-svg', viewBox: `0 0 ${w} ${hgt}`, width: '100%' }, nodes);
}

function renderScatter(chart: AiChartSpec): VNode {
  const series = chart.series || [];
  const w = 360;
  const hgt = 220;
  const pad = { t: 20, r: 16, b: 40, l: 40 };
  const iw = w - pad.l - pad.r;
  const ih = hgt - pad.t - pad.b;
  let maxX = 1;
  let maxY = 1;
  for (const s of series) {
    for (const p of s.points || []) {
      maxX = Math.max(maxX, num(p[0]));
      maxY = Math.max(maxY, num(p[1]));
    }
  }
  const nodes: VNode[] = [
    h('line', {
      x1: pad.l,
      y1: pad.t,
      x2: pad.l,
      y2: pad.t + ih,
      class: 'ai-chart-axis',
    }),
    h('line', {
      x1: pad.l,
      y1: pad.t + ih,
      x2: pad.l + iw,
      y2: pad.t + ih,
      class: 'ai-chart-axis',
    }),
  ];
  if (chart.xLabel) {
    nodes.push(
      h(
        'text',
        {
          x: pad.l + iw / 2,
          y: hgt - 8,
          'text-anchor': 'middle',
          class: 'ai-chart-label',
        },
        chart.xLabel,
      ),
    );
  }
  if (chart.yLabel) {
    nodes.push(
      h(
        'text',
        {
          x: 12,
          y: pad.t + ih / 2,
          'text-anchor': 'middle',
          transform: `rotate(-90 12 ${pad.t + ih / 2})`,
          class: 'ai-chart-label',
        },
        chart.yLabel,
      ),
    );
  }
  series.forEach((s, si) => {
    for (const p of s.points || []) {
      const x = pad.l + (num(p[0]) / maxX) * iw;
      const y = pad.t + ih - (num(p[1]) / maxY) * ih;
      nodes.push(
        h('circle', {
          cx: x,
          cy: y,
          r: 4,
          fill: COLORS[si % COLORS.length],
          'fill-opacity': 0.85,
        }),
      );
    }
  });
  return h('svg', { class: 'ai-chart-svg', viewBox: `0 0 ${w} ${hgt}`, width: '100%' }, nodes);
}

function renderRadar(chart: AiChartSpec): VNode {
  const indicators = chart.indicators || [];
  const series = chart.series || [];
  const n = Math.max(3, indicators.length);
  const w = 280;
  const hgt = 240;
  const cx = w / 2;
  const cy = hgt / 2 + 4;
  const r = 80;
  const nodes: VNode[] = [];

  for (let ring = 1; ring <= 4; ring++) {
    const rr = (r * ring) / 4;
    const pts = Array.from({ length: n }, (_, i) => {
      const a = -Math.PI / 2 + (i * 2 * Math.PI) / n;
      return [cx + rr * Math.cos(a), cy + rr * Math.sin(a)];
    });
    nodes.push(
      h('polygon', {
        points: pts.map((p) => p.join(',')).join(' '),
        class: 'ai-chart-radar-grid',
      }),
    );
  }

  indicators.forEach((ind, i) => {
    const a = -Math.PI / 2 + (i * 2 * Math.PI) / n;
    const x = cx + r * Math.cos(a);
    const y = cy + r * Math.sin(a);
    nodes.push(
      h('line', {
        x1: cx,
        y1: cy,
        x2: x,
        y2: y,
        class: 'ai-chart-radar-axis',
      }),
    );
    nodes.push(
      h(
        'text',
        {
          x: cx + (r + 14) * Math.cos(a),
          y: cy + (r + 14) * Math.sin(a),
          'text-anchor': 'middle',
          'dominant-baseline': 'middle',
          class: 'ai-chart-label',
        },
        truncate(ind.name, 6),
      ),
    );
  });

  series.forEach((s, si) => {
    const pts = indicators.map((ind, i) => {
      const max = Math.max(1, num(ind.max));
      const v = num(s.data?.[i]);
      const rr = (Math.min(v, max) / max) * r;
      const a = -Math.PI / 2 + (i * 2 * Math.PI) / n;
      return [cx + rr * Math.cos(a), cy + rr * Math.sin(a)];
    });
    nodes.push(
      h('polygon', {
        points: pts.map((p) => p.join(',')).join(' '),
        fill: COLORS[si % COLORS.length],
        'fill-opacity': 0.25,
        stroke: COLORS[si % COLORS.length],
        'stroke-width': 2,
      }),
    );
  });

  return h('svg', { class: 'ai-chart-svg', viewBox: `0 0 ${w} ${hgt}`, width: '100%' }, nodes);
}

function pieSlice(cx: number, cy: number, r: number, a0: number, a1: number): string {
  const x0 = cx + r * Math.cos(a0);
  const y0 = cy + r * Math.sin(a0);
  const x1 = cx + r * Math.cos(a1);
  const y1 = cy + r * Math.sin(a1);
  const large = a1 - a0 > Math.PI ? 1 : 0;
  return `M${cx},${cy} L${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1} Z`;
}

function donutSlice(
  cx: number,
  cy: number,
  r: number,
  ir: number,
  a0: number,
  a1: number,
): string {
  const x0 = cx + r * Math.cos(a0);
  const y0 = cy + r * Math.sin(a0);
  const x1 = cx + r * Math.cos(a1);
  const y1 = cy + r * Math.sin(a1);
  const ix0 = cx + ir * Math.cos(a1);
  const iy0 = cy + ir * Math.sin(a1);
  const ix1 = cx + ir * Math.cos(a0);
  const iy1 = cy + ir * Math.sin(a0);
  const large = a1 - a0 > Math.PI ? 1 : 0;
  return `M${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1} L${ix0},${iy0} A${ir},${ir} 0 ${large} 0 ${ix1},${iy1} Z`;
}

function num(v: unknown): number {
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}

function truncate(s: string, max: number): string {
  if (!s) return '';
  return s.length <= max ? s : s.slice(0, max - 1) + '…';
}
