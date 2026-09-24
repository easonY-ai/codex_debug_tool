import assert from 'node:assert/strict'
import test from 'node:test'
import { buildTimelineRows } from './timeline.ts'
import type { Node } from './data.ts'

function node(id: string, source: Node['source'], start: number, operationId?: string): Node {
  return {
    id,
    title: id,
    source,
    lane: 'test',
    start,
    end: start + 1,
    state: '完成',
    raw: { id },
    operationId
  }
}

test('keeps every operation and span, including empty and unknown rows', () => {
  const rows = buildTimelineRows([
    node('tool', 'OTel 执行', 5),
    node('decision', 'OTel 执行', 2),
    node('span-b', 'OTel 性能', 6, 'tool'),
    node('orphan', 'OTel 性能', 3),
    node('span-a', 'OTel 性能', 5, 'tool'),
    node('content', 'Transcript 内容', 7)
  ])

  assert.deepEqual(rows.map(row => row.operation?.id ?? '未知操作'), [
    'decision', '未知操作', 'tool', 'content'
  ])
  assert.deepEqual(rows.find(row => row.operation?.id === 'tool')?.spans.map(span => span.id), [
    'span-a', 'span-b'
  ])
  assert.deepEqual(rows.find(row => row.operation?.id === 'decision')?.spans, [])
  assert.deepEqual(rows.find(row => row.operation === null)?.spans.map(span => span.id), ['orphan'])
})

test('unresolved operation identity does not invent a matching operation', () => {
  const rows = buildTimelineRows([
    node('tool', 'OTel 执行', 1),
    node('span', 'OTel 性能', 2, 'other-tool')
  ])

  assert.equal(rows.length, 2)
  assert.equal(rows[1]?.operation, null)
  assert.equal(rows[1]?.spans[0]?.id, 'span')
})
