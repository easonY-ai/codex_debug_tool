import { describe, expect, it } from 'vitest'
import { buildAgentTimelineItems, transcriptMappingDescription } from '../src/data/traceMapper'

describe('transcript content mapping', () => {
  it('adds JSONL content and evidence to the matching Hook nodes', () => {
    const events = [
      { id: 1, observedAt: 1000, parseStatus: 'NORMALIZED', rawJson: JSON.stringify({ hook_event_name:'UserPromptSubmit', turn_id:'turn-demo' }) },
      { id: 2, observedAt: 1100, parseStatus: 'NORMALIZED', rawJson: JSON.stringify({ hook_event_name:'PreToolUse', turn_id:'turn-demo', tool_use_id:'tool-demo', tool_name:'Bash' }) },
      { id: 3, observedAt: 1200, parseStatus: 'NORMALIZED', rawJson: JSON.stringify({ hook_event_name:'PostToolUse', turn_id:'turn-demo', tool_use_id:'tool-demo', tool_name:'Bash' }) },
      { id: 4, observedAt: 1300, parseStatus: 'NORMALIZED', rawJson: JSON.stringify({ hook_event_name:'Stop', turn_id:'turn-demo' }) },
    ]
    const supplements = [
      { id: 11, hookNodeType:'TURN', hookNodeId:'turn-demo', contentKind:'USER_INPUT', mappingLevel:'BOUNDED', adapterVersion:'codex-2026-09', contentText:'synthetic question', rawJson:'{"type":"event_msg"}' },
      { id: 12, hookNodeType:'TOOL', hookNodeId:'tool-demo', callId:'tool-demo', contentKind:'TOOL_INPUT', mappingLevel:'EXACT', adapterVersion:'codex-2026-09', contentText:'printf demo', rawJson:'{"type":"response_item"}' },
      { id: 13, hookNodeType:'TOOL', hookNodeId:'tool-demo', callId:'tool-demo', contentKind:'TOOL_OUTPUT', mappingLevel:'EXACT', adapterVersion:'codex-2026-09', contentText:'demo', rawJson:'{"type":"response_item"}' },
      { id: 14, hookNodeType:'TURN', hookNodeId:'turn-demo', contentKind:'MODEL_OUTPUT', mappingLevel:'BOUNDED', adapterVersion:'codex-2026-09', contentText:'final demo', rawJson:'{"type":"event_msg"}' },
    ]

    const items = buildAgentTimelineItems(events, supplements, 1000)

    expect(items[0]).toMatchObject({ input:'synthetic question', source:'Hook + JSONL' })
    expect(items[1]).toMatchObject({ input:'printf demo', source:'Hook + JSONL' })
    expect(items[2]).toMatchObject({ output:'demo', source:'Hook + JSONL' })
    expect(items[3]).toMatchObject({ output:'final demo', source:'Hook + JSONL' })
    expect(items[1].jsonlEvidence?.[0]).toMatchObject({
      mappingLevel:'EXACT',
      adapterVersion:'codex-2026-09',
      hookNodeId:'tool-demo',
      callId:'tool-demo',
    })
    expect(transcriptMappingDescription(items[1].jsonlEvidence![0]))
      .toContain('Hook tool_use_id 与 JSONL call_id 相等')
    expect(transcriptMappingDescription(items[0].jsonlEvidence![0]))
      .toContain('不声明工具级精确关联')
  })
})
