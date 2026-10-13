import {
  AiClarify,
  formatClarifyReply,
  validateClarify,
  type AiClarifyForm,
} from '@ysmp/ai-assistant-core';
import { h, type VNode } from 'vue';

export function initClarifyValues(form: AiClarifyForm): Record<string, string | string[]> {
  const values: Record<string, string | string[]> = {};
  for (const f of form.fields || []) {
    if (f.type === AiClarify.FIELD_MULTI) {
      values[f.id] = f.defaultValue ? [f.defaultValue] : [];
    } else {
      values[f.id] = f.defaultValue || '';
    }
  }
  return values;
}

export function renderClarifyForm(opts: {
  form: AiClarifyForm;
  values: Record<string, string | string[]>;
  answered: boolean;
  error?: string;
  onError: (msg: string) => void;
  onSubmit: (message: string) => void;
  /** 触发外层重新渲染 */
  onChange: () => void;
}): VNode {
  const { form, values, answered } = opts;

  const submit = () => {
    const err = validateClarify(form, values);
    if (err) {
      opts.onError(err);
      return;
    }
    opts.onError('');
    opts.onSubmit(formatClarifyReply(form, values));
  };

  return h('div', { class: ['ai-clarify', answered ? 'ai-clarify-done' : ''] }, [
    h('div', { class: 'ai-clarify-title' }, form.title || '请补充信息'),
    form.description
      ? h('div', { class: 'ai-clarify-desc' }, form.description)
      : null,
    h(
      'div',
      { class: 'ai-clarify-fields' },
      (form.fields || []).map((field) =>
        h('div', { class: 'ai-clarify-field', key: field.id }, [
          h('label', { class: 'ai-clarify-label' }, [
            field.label,
            field.required ? h('span', { class: 'ai-clarify-req' }, '*') : null,
          ]),
          renderControl(field, values, answered, opts.onChange),
        ]),
      ),
    ),
    opts.error ? h('div', { class: 'ai-clarify-error' }, opts.error) : null,
    answered
      ? h('div', { class: 'ai-clarify-answered' }, '已提交')
      : h(
          'button',
          {
            class: 'ai-clarify-submit',
            type: 'button',
            onClick: submit,
          },
          '提交补充',
        ),
  ]);
}

function renderControl(
  field: {
    id: string;
    type: string;
    placeholder?: string;
    options?: { value: string; label: string }[];
  },
  values: Record<string, string | string[]>,
  disabled: boolean,
  onChange: () => void,
): VNode {
  const type = field.type || AiClarify.FIELD_TEXT;
  if (type === AiClarify.FIELD_TEXTAREA) {
    return h('textarea', {
      class: 'ai-clarify-input',
      disabled,
      placeholder: field.placeholder || '',
      value: String(values[field.id] ?? ''),
      onInput: (e: Event) => {
        values[field.id] = (e.target as HTMLTextAreaElement).value;
        onChange();
      },
    });
  }
  if (type === AiClarify.FIELD_SELECT) {
    return h(
      'select',
      {
        class: 'ai-clarify-input',
        disabled,
        value: String(values[field.id] ?? ''),
        onChange: (e: Event) => {
          values[field.id] = (e.target as HTMLSelectElement).value;
          onChange();
        },
      },
      [
        h('option', { value: '' }, '请选择'),
        ...(field.options || []).map((o) =>
          h('option', { value: o.value }, o.label || o.value),
        ),
      ],
    );
  }
  if (type === AiClarify.FIELD_SINGLE) {
    return h(
      'div',
      { class: 'ai-clarify-options' },
      (field.options || []).map((o) =>
        h('label', { class: 'ai-clarify-option' }, [
          h('input', {
            type: 'radio',
            name: `ai-clarify-${field.id}`,
            disabled,
            value: o.value,
            checked: values[field.id] === o.value,
            onChange: () => {
              values[field.id] = o.value;
              onChange();
            },
          }),
          h('span', o.label || o.value),
        ]),
      ),
    );
  }
  if (type === AiClarify.FIELD_MULTI) {
    const selected = Array.isArray(values[field.id])
      ? (values[field.id] as string[])
      : [];
    return h(
      'div',
      { class: 'ai-clarify-options' },
      (field.options || []).map((o) =>
        h('label', { class: 'ai-clarify-option' }, [
          h('input', {
            type: 'checkbox',
            disabled,
            value: o.value,
            checked: selected.includes(o.value),
            onChange: (e: Event) => {
              const checked = (e.target as HTMLInputElement).checked;
              const next = new Set(selected);
              if (checked) next.add(o.value);
              else next.delete(o.value);
              values[field.id] = [...next];
              onChange();
            },
          }),
          h('span', o.label || o.value),
        ]),
      ),
    );
  }
  return h('input', {
    class: 'ai-clarify-input',
    type: type === AiClarify.FIELD_NUMBER ? 'number' : 'text',
    disabled,
    placeholder: field.placeholder || '',
    value: String(values[field.id] ?? ''),
    onInput: (e: Event) => {
      values[field.id] = (e.target as HTMLInputElement).value;
      onChange();
    },
  });
}
