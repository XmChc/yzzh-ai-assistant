import { AiMentionKeywords } from '@ysmp/ai-assistant-core';

/** @ 引用：token 须为 agent/skill/tool 的前缀（忽略大小写）才保持弹层 */

export type MentionKind = 'agent' | 'skill' | 'tool';

const KEYWORDS: Record<MentionKind, string> = {
  agent: AiMentionKeywords.AGENT,
  skill: AiMentionKeywords.SKILL,
  tool: AiMentionKeywords.TOOL,
};

export interface MentionTrigger {
  kind: MentionKind;
  /** 含 @ 的起始下标 */
  start: number;
  /** token（不含 @） */
  token: string;
}

/**
 * 从光标前文本解析当前是否处于合法 @agent/@skill/@tool 前缀。
 */
export function detectMention(textBeforeCursor: string): MentionTrigger | null {
  const at = textBeforeCursor.lastIndexOf('@');
  if (at < 0) return null;
  if (at > 0) {
    const prev = textBeforeCursor[at - 1];
    if (prev && !/\s/.test(prev)) return null;
  }
  const token = textBeforeCursor.slice(at + 1);
  if (!token) return null;
  if (/\s/.test(token)) return null;

  const lower = token.toLowerCase();
  for (const kind of Object.keys(KEYWORDS) as MentionKind[]) {
    const kw = KEYWORDS[kind];
    if (kw.startsWith(lower)) {
      return { kind, start: at, token };
    }
  }
  return null;
}
