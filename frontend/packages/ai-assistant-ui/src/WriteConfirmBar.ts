import { h, type VNode } from 'vue';

export interface WriteConfirmItem {
  id: string;
  name: string;
  arguments?: string;
}

/**
 * 写操作确认条：展示待执行 Tool，确认 / 取消。
 */
export function renderWriteConfirmBar(opts: {
  items: WriteConfirmItem[];
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}): VNode | null {
  const { items, loading, onConfirm, onCancel } = opts;
  if (!items.length) return null;

  const names = items.map((p) => p.name).join('、');
  const preview = items
    .map((p) => {
      const args = (p.arguments || '').trim();
      if (!args || args === '{}') return p.name;
      const short = args.length > 80 ? `${args.slice(0, 80)}…` : args;
      return `${p.name}(${short})`;
    })
    .join('；');

  return h('div', { class: 'ai-write-confirm', role: 'alertdialog', 'aria-label': '写操作确认' }, [
    h('div', { class: 'ai-write-confirm-body' }, [
      h('div', { class: 'ai-write-confirm-title' }, '写操作待确认'),
      h('div', { class: 'ai-write-confirm-text' }, `将执行：${names}`),
      preview && preview !== names
        ? h('div', { class: 'ai-write-confirm-preview', title: preview }, preview)
        : null,
    ]),
    h('div', { class: 'ai-write-confirm-actions' }, [
      h(
        'button',
        {
          type: 'button',
          class: 'ai-write-confirm-ok',
          disabled: !!loading,
          onClick: onConfirm,
        },
        '确认执行',
      ),
      h(
        'button',
        {
          type: 'button',
          class: 'ai-write-confirm-cancel',
          disabled: !!loading,
          onClick: onCancel,
        },
        '取消',
      ),
    ]),
  ]);
}
