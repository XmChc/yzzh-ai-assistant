import {
  getToolResultField,
  getToolResultPage,
  type AiToolDataRef,
  type AiToolResultPage,
} from '@ysmp/ai-assistant-core';
import { defineComponent, h, onMounted, ref, type PropType } from 'vue';

function cellText(v: unknown): string {
  if (v == null) return '';
  if (typeof v === 'object') {
    const o = v as Record<string, unknown>;
    if (o._nested) return `[列表 ${o._count ?? '?'} 条 · 点击展开]`;
    try {
      return JSON.stringify(v);
    } catch {
      return String(v);
    }
  }
  return String(v);
}

function isNestedMark(v: unknown): boolean {
  return !!v && typeof v === 'object' && (v as Record<string, unknown>)._nested === true;
}

/**
 * Tool 大数据结果分页面板（支持嵌套字段展开）。
 */
export const ToolResultPanel = defineComponent({
  name: 'ToolResultPanel',
  props: {
    data: { type: Object as PropType<AiToolDataRef>, required: true },
  },
  setup(props) {
    const page = ref(1);
    const pageSize = ref(props.data.pageSize || 10);
    const loading = ref(false);
    const error = ref('');
    const result = ref<AiToolResultPage | null>(null);

    const nestOpen = ref(false);
    const nestField = ref('');
    const nestRowIndex = ref(0);
    const nestPage = ref(1);
    const nestResult = ref<AiToolResultPage | null>(null);
    const nestLoading = ref(false);

    async function load() {
      loading.value = true;
      error.value = '';
      try {
        result.value = await getToolResultPage({
          refId: props.data.refId,
          page: page.value,
          pageSize: pageSize.value,
        });
      } catch (e: unknown) {
        error.value = e instanceof Error ? e.message : String(e);
      } finally {
        loading.value = false;
      }
    }

    async function loadNest() {
      nestLoading.value = true;
      try {
        nestResult.value = await getToolResultField({
          refId: props.data.refId,
          rowIndex: nestRowIndex.value,
          field: nestField.value,
          page: nestPage.value,
          pageSize: pageSize.value,
        });
      } catch (e: unknown) {
        error.value = e instanceof Error ? e.message : String(e);
      } finally {
        nestLoading.value = false;
      }
    }

    onMounted(() => {
      // 先用 sample 顶一页，再拉正式分页
      if (props.data.sampleRows?.length) {
        result.value = {
          refId: props.data.refId,
          toolCode: props.data.toolCode,
          shape: props.data.shape,
          fields: props.data.fields,
          rows: props.data.sampleRows,
          page: 1,
          pageSize: pageSize.value,
          total: props.data.total || props.data.sampleRows.length,
          reportedTotal: props.data.reportedTotal,
        };
      }
      void load();
    });

    function totalPages(total: number, size: number) {
      return Math.max(1, Math.ceil(total / Math.max(1, size)));
    }

    function formatStatLine(name: string, col: Record<string, unknown>): string {
      const parts: string[] = [name];
      if (col.type === 'number' || col.type === 'mixed') {
        if (col.sum != null) parts.push(`合计 ${col.sum}`);
        if (col.avg != null) parts.push(`均值 ${Number(col.avg).toFixed(2)}`);
        if (col.min != null && col.max != null) parts.push(`范围 ${col.min}~${col.max}`);
      }
      const top = col.top as { value: string; count: number }[] | undefined;
      if (top?.length) {
        parts.push(
          '分布 ' +
            top
              .slice(0, 4)
              .map((t) => `${t.value}×${t.count}`)
              .join('、'),
        );
      } else if (col.nulls) {
        parts.push(`空值 ${col.nulls}`);
      }
      return parts.join(' · ');
    }

    function renderCoverage() {
      const c = props.data.coverage;
      if (!c) return null;
      const covered = c.coveredRows ?? props.data.total ?? 0;
      const reported = c.reportedTotal ?? props.data.reportedTotal;
      const complete = c.complete === true;
      const ratio =
        reported != null && reported > 0
          ? Math.min(100, Math.round((covered / reported) * 100))
          : complete
            ? 100
            : null;
      return h(
        'div',
        {
          class: ['ai-tool-coverage', complete ? 'is-complete' : 'is-partial'],
        },
        [
          h(
            'div',
            { class: 'ai-tool-coverage-title' },
            complete ? '数据已覆盖全部' : '部分覆盖（非全量合计）',
          ),
          h(
            'div',
            { class: 'ai-tool-coverage-meta' },
            reported != null
              ? `已拉取 ${covered} / ${reported} 行${ratio != null ? `（${ratio}%）` : ''}`
              : `已拉取 ${covered} 行`,
          ),
          !complete
            ? h(
                'div',
                { class: 'ai-tool-coverage-hint' },
                '下方合计仅基于已拉取行；缩小时间范围或使用汇总接口可得到全量结果。',
              )
            : null,
        ],
      );
    }

    function renderStats() {
      const stats = props.data.stats as
        | { columns?: Record<string, Record<string, unknown>>; scannedRows?: number; sampled?: boolean }
        | undefined;
      const columns = stats?.columns;
      if (!columns || !Object.keys(columns).length) return null;
      const lines = Object.entries(columns)
        .slice(0, 8)
        .map(([name, col]) => formatStatLine(name, col || {}));
      return h('div', { class: 'ai-tool-stats' }, [
        h(
          'div',
          { class: 'ai-tool-stats-title' },
          stats?.sampled
            ? `列汇总（已扫描 ${stats.scannedRows ?? '?'} 行，采样）`
            : '列汇总',
        ),
        ...lines.map((line) => h('div', { class: 'ai-tool-stats-line' }, line)),
      ]);
    }

    function renderTable(r: AiToolResultPage, nested: boolean) {
      const fields = r.fields?.length
        ? r.fields
        : r.rows?.[0]
          ? Object.keys(r.rows[0])
          : [];
      const rows = r.rows || [];
      return h('div', { class: 'ai-tool-table-wrap' }, [
        h('table', { class: 'ai-tool-table' }, [
          h('thead', [
            h(
              'tr',
              fields.map((f) => h('th', f)),
            ),
          ]),
          h(
            'tbody',
            rows.length
              ? rows.map((row, ri) =>
                  h(
                    'tr',
                    fields.map((f) => {
                      const val = row[f];
                      if (!nested && isNestedMark(val)) {
                        return h('td', [
                          h(
                            'button',
                            {
                              class: 'ai-tool-nest-btn',
                              type: 'button',
                              onClick: () => {
                                const absoluteIndex = (r.page - 1) * r.pageSize + ri;
                                nestRowIndex.value = absoluteIndex;
                                nestField.value = f;
                                nestPage.value = 1;
                                nestOpen.value = true;
                                void loadNest();
                              },
                            },
                            cellText(val),
                          ),
                        ]);
                      }
                      return h('td', { title: cellText(val) }, cellText(val));
                    }),
                  ),
                )
              : [h('tr', [h('td', { colspan: fields.length || 1 }, '暂无数据')])],
          ),
        ]),
      ]);
    }

    function renderPager(
      cur: number,
      total: number,
      size: number,
      onPrev: () => void,
      onNext: () => void,
    ) {
      const pages = totalPages(total, size);
      return h('div', { class: 'ai-tool-pager' }, [
        h(
          'button',
          {
            type: 'button',
            disabled: cur <= 1 || loading.value || nestLoading.value,
            onClick: onPrev,
          },
          '上一页',
        ),
        h('span', `${cur} / ${pages}（共 ${total} 条）`),
        h(
          'button',
          {
            type: 'button',
            disabled: cur >= pages || loading.value || nestLoading.value,
            onClick: onNext,
          },
          '下一页',
        ),
      ]);
    }

    return () => {
      const r = result.value;
      return h('div', { class: 'ai-tool-panel' }, [
        h('div', { class: 'ai-tool-panel-head' }, [
          h('strong', props.data.toolCode || 'Tool 结果'),
          h(
            'span',
            { class: 'ai-tool-meta' },
            `共 ${props.data.reportedTotal ?? props.data.total ?? r?.total ?? 0} 条 · 分页查看`,
          ),
        ]),
        error.value ? h('div', { class: 'ai-tool-error' }, error.value) : null,
        renderCoverage(),
        renderStats(),
        loading.value && !r ? h('div', { class: 'ai-tool-loading' }, '加载中…') : null,
        r ? renderTable(r, false) : null,
        r
          ? renderPager(
              r.page,
              r.total,
              r.pageSize,
              () => {
                if (page.value > 1) {
                  page.value -= 1;
                  void load();
                }
              },
              () => {
                page.value += 1;
                void load();
              },
            )
          : null,
        nestOpen.value
          ? h('div', { class: 'ai-tool-nest' }, [
              h('div', { class: 'ai-tool-panel-head' }, [
                h('strong', `嵌套：${nestField.value}`),
                h(
                  'button',
                  {
                    type: 'button',
                    class: 'ai-tool-close',
                    onClick: () => {
                      nestOpen.value = false;
                      nestResult.value = null;
                    },
                  },
                  '关闭',
                ),
              ]),
              nestLoading.value && !nestResult.value
                ? h('div', { class: 'ai-tool-loading' }, '加载中…')
                : null,
              nestResult.value ? renderTable(nestResult.value, true) : null,
              nestResult.value
                ? renderPager(
                    nestResult.value.page,
                    nestResult.value.total,
                    nestResult.value.pageSize,
                    () => {
                      if (nestPage.value > 1) {
                        nestPage.value -= 1;
                        void loadNest();
                      }
                    },
                    () => {
                      nestPage.value += 1;
                      void loadNest();
                    },
                  )
                : null,
            ])
          : null,
      ]);
    };
  },
});
