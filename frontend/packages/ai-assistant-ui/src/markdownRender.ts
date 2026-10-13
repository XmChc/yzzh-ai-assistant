/**
 * 轻量 Markdown → 安全 HTML（先转义再解析，无第三方依赖）。
 * 覆盖 LLM 常见输出：标题、列表、表格、代码块、引用、链接、粗斜体。
 */

export function renderMarkdown(src: string): string {
  if (!src) return '';
  const text = src.replace(/\r\n/g, '\n');
  const blocks = splitBlocks(text);
  return blocks.map(renderBlock).join('');
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function splitBlocks(text: string): string[] {
  const lines = text.split('\n');
  const blocks: string[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (/^```/.test(line)) {
      const lang = line.slice(3).trim();
      const body: string[] = [];
      i += 1;
      while (i < lines.length && !/^```/.test(lines[i])) {
        body.push(lines[i]);
        i += 1;
      }
      if (i < lines.length) i += 1;
      blocks.push(`__CODE__${lang}\n${body.join('\n')}`);
      continue;
    }
    if (/^\|(.+\|)+/.test(line) && i + 1 < lines.length && /^\|?\s*:?-+:?\s*\|/.test(lines[i + 1])) {
      const table: string[] = [line];
      i += 1;
      while (i < lines.length && /^\|/.test(lines[i])) {
        table.push(lines[i]);
        i += 1;
      }
      blocks.push(`__TABLE__\n${table.join('\n')}`);
      continue;
    }
    if (/^#{1,6}\s/.test(line) || /^>\s?/.test(line) || /^(-{3,}|\*{3,}|_{3,})\s*$/.test(line)
      || /^[-*+]\s+/.test(line) || /^\d+\.\s+/.test(line)) {
      const chunk: string[] = [line];
      i += 1;
      const isList = /^[-*+]\s+/.test(line) || /^\d+\.\s+/.test(line);
      const isQuote = /^>\s?/.test(line);
      while (i < lines.length) {
        const n = lines[i];
        if (!n.trim()) break;
        if (isList && (/^[-*+]\s+/.test(n) || /^\d+\.\s+/.test(n) || /^\s{2,}/.test(n))) {
          chunk.push(n);
          i += 1;
          continue;
        }
        if (isQuote && /^>\s?/.test(n)) {
          chunk.push(n);
          i += 1;
          continue;
        }
        if (!isList && !isQuote) break;
        break;
      }
      blocks.push(chunk.join('\n'));
      continue;
    }
    if (!line.trim()) {
      i += 1;
      continue;
    }
    const para: string[] = [line];
    i += 1;
    while (i < lines.length && lines[i].trim()
      && !/^```/.test(lines[i])
      && !/^#{1,6}\s/.test(lines[i])
      && !/^>\s?/.test(lines[i])
      && !/^[-*+]\s+/.test(lines[i])
      && !/^\d+\.\s+/.test(lines[i])
      && !/^(-{3,}|\*{3,}|_{3,})\s*$/.test(lines[i])
      && !(/^\|(.+\|)+/.test(lines[i]))) {
      para.push(lines[i]);
      i += 1;
    }
    blocks.push(para.join('\n'));
  }
  return blocks;
}

function renderBlock(block: string): string {
  if (block.startsWith('__CODE__')) {
    const nl = block.indexOf('\n');
    const lang = escapeHtml(block.slice(8, nl).trim());
    const code = escapeHtml(block.slice(nl + 1));
    return `<pre class="ai-md-pre"><code class="ai-md-code"${lang ? ` data-lang="${lang}"` : ''}>${code}</code></pre>`;
  }
  if (block.startsWith('__TABLE__\n')) {
    return renderTable(block.slice('__TABLE__\n'.length));
  }
  if (/^(-{3,}|\*{3,}|_{3,})\s*$/.test(block.trim())) {
    return '<hr class="ai-md-hr"/>';
  }
  const heading = block.match(/^(#{1,6})\s+(.+)$/);
  if (heading && !block.includes('\n')) {
    const level = heading[1].length;
    return `<h${level} class="ai-md-h">${inline(heading[2])}</h${level}>`;
  }
  if (/^>\s?/.test(block)) {
    const body = block
      .split('\n')
      .map((l) => l.replace(/^>\s?/, ''))
      .join('<br/>');
    return `<blockquote class="ai-md-quote">${inline(body)}</blockquote>`;
  }
  if (/^[-*+]\s+/.test(block) || /^\d+\.\s+/.test(block)) {
    const ordered = /^\d+\.\s+/.test(block);
    const tag = ordered ? 'ol' : 'ul';
    const items = block.split('\n').filter((l) => l.trim()).map((l) => {
      const t = l.replace(/^[-*+]\s+/, '').replace(/^\d+\.\s+/, '');
      return `<li>${inline(t)}</li>`;
    });
    return `<${tag} class="ai-md-list">${items.join('')}</${tag}>`;
  }
  return `<p class="ai-md-p">${inline(block.replace(/\n/g, '<br/>'))}</p>`;
}

function renderTable(raw: string): string {
  const rows = raw.split('\n').filter((r) => r.trim());
  if (rows.length < 2) return `<p class="ai-md-p">${inline(raw)}</p>`;
  const parseRow = (row: string) =>
    row
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((c) => c.trim());
  const head = parseRow(rows[0]);
  const body = rows.slice(2).map(parseRow);
  const th = head.map((c) => `<th>${inline(c)}</th>`).join('');
  const tr = body
    .map((r) => `<tr>${r.map((c) => `<td>${inline(c)}</td>`).join('')}</tr>`)
    .join('');
  return `<div class="ai-md-table-wrap"><table class="ai-md-table"><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table></div>`;
}

function inline(s: string): string {
  // 已含少量安全标签（如 br）时，仅转义纯文本段；此处输入多为未转义原文
  let out = escapeHtml(s).replace(/&lt;br\/&gt;/g, '<br/>');
  out = out.replace(/!\[([^\]]*)]\((https?:[^)\s]+)\)/g, '<img class="ai-md-img" alt="$1" src="$2" loading="lazy"/>');
  out = out.replace(/\[([^\]]+)]\((https?:[^)\s]+)\)/g, '<a class="ai-md-a" href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
  out = out.replace(/`([^`]+)`/g, '<code class="ai-md-inline">$1</code>');
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/__([^_]+)__/g, '<strong>$1</strong>');
  out = out.replace(/~~([^~]+)~~/g, '<del>$1</del>');
  // 斜体放在加粗之后，避免吃掉 **
  out = out.replace(/\*([^*\n]+)\*/g, '<em>$1</em>');
  out = out.replace(/_([^_\n]+)_/g, '<em>$1</em>');
  return out;
}
