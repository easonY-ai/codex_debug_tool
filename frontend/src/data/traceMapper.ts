import type { TimelineItem, TranscriptEvidence } from '../types'

export interface HookEventWire {
  id: number
  observedAt: number
  parseStatus: string
  rawJson: string
}

export interface TranscriptSupplementWire {
  id: number
  hookNodeType: 'TURN' | 'TOOL'
  hookNodeId: string
  callId?: string
  contentKind: TranscriptEvidence['contentKind']
  mappingLevel: TranscriptEvidence['mappingLevel']
  adapterVersion: string
  contentText: string
  rawJson: string
}

export function buildAgentTimelineItems(
  events: HookEventWire[],
  supplements: TranscriptSupplementWire[],
  turnStartedAt: number,
): TimelineItem[] {
  return events.map((event) => {
    const raw = JSON.parse(event.rawJson) as Record<string, unknown>
    const allowedKinds = contentKinds(raw)
    const matching = supplements.filter((supplement) =>
      matchesHookEvent(raw, supplement) && allowedKinds.includes(supplement.contentKind))
    const evidence = matching.map(toEvidence)
    const input = contentFor(matching, inputKind(raw))
    const output = contentFor(matching, outputKind(raw))
    return {
      id: `hook-${event.id}`,
      layer: 'Agent 行为',
      lane: raw.tool_name ? '工具' : raw.hook_event_name === 'UserPromptSubmit' ? '用户' : '模型',
      title: String(raw.hook_event_name ?? 'Hook event'),
      startMs: Math.max(0, Number(event.observedAt) - turnStartedAt),
      durationMs: 0,
      status: event.parseStatus === 'NORMALIZED' ? 'success' : 'warning',
      source: evidence.length ? 'Hook + JSONL' : 'Hook',
      description: evidence.length
        ? 'Hook 提供生命周期身份，JSONL 补充可见内容。'
        : 'Codex Hook 原始生命周期事件，JSONL 内容尚未补齐。',
      input,
      output,
      raw,
      jsonlEvidence: evidence,
    }
  })
}

function contentKinds(raw: Record<string, unknown>): TranscriptEvidence['contentKind'][] {
  if (raw.hook_event_name === 'UserPromptSubmit') return ['USER_INPUT']
  if (raw.hook_event_name === 'PreToolUse') return ['TOOL_INPUT']
  if (raw.hook_event_name === 'PostToolUse') return ['TOOL_OUTPUT']
  if (raw.hook_event_name === 'Stop') return ['MODEL_OUTPUT', 'REASONING_SUMMARY']
  return []
}

function matchesHookEvent(raw: Record<string, unknown>, supplement: TranscriptSupplementWire): boolean {
  if (supplement.hookNodeType === 'TOOL') {
    return raw.tool_use_id === supplement.hookNodeId
  }
  return raw.turn_id === supplement.hookNodeId
}

function inputKind(raw: Record<string, unknown>): TranscriptEvidence['contentKind'] | undefined {
  if (raw.hook_event_name === 'UserPromptSubmit') return 'USER_INPUT'
  if (raw.hook_event_name === 'PreToolUse') return 'TOOL_INPUT'
  return undefined
}

function outputKind(raw: Record<string, unknown>): TranscriptEvidence['contentKind'] | undefined {
  if (raw.hook_event_name === 'PostToolUse') return 'TOOL_OUTPUT'
  if (raw.hook_event_name === 'Stop') return 'MODEL_OUTPUT'
  return undefined
}

function contentFor(
  supplements: TranscriptSupplementWire[],
  kind: TranscriptEvidence['contentKind'] | undefined,
): string | undefined {
  if (!kind) return undefined
  const matches = supplements.filter((supplement) => supplement.contentKind === kind)
  return matches.length ? matches.map((supplement) => supplement.contentText).join('\n') : undefined
}

function toEvidence(supplement: TranscriptSupplementWire): TranscriptEvidence {
  return {
    id: supplement.id,
    hookNodeId: supplement.hookNodeId,
    callId: supplement.callId,
    contentKind: supplement.contentKind,
    mappingLevel: supplement.mappingLevel,
    adapterVersion: supplement.adapterVersion,
    contentText: supplement.contentText,
    raw: JSON.parse(supplement.rawJson) as Record<string, unknown>,
  }
}

export function transcriptMappingDescription(evidence: TranscriptEvidence): string {
  if (evidence.mappingLevel === 'EXACT') {
    return '精确内容关联：Hook tool_use_id 与 JSONL call_id 相等。'
  }
  return '区间内容关联：JSONL 内容限定到当前 Hook Turn，不声明工具级精确关联。'
}
