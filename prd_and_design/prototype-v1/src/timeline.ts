import type { Node } from './data.ts'

export interface TimelineRow {
  id: string
  operation: Node | null
  spans: Node[]
  at: number
}

export function buildTimelineRows(nodes: Node[]): TimelineRow[] {
  const operations = nodes.filter(node => node.source !== 'OTel 性能')
  const operationIds = new Set(operations.map(node => node.id))
  const spans = nodes.filter(node => node.source === 'OTel 性能')

  const rows: TimelineRow[] = operations.map(operation => ({
    id: operation.id,
    operation,
    spans: spans
      .filter(span => span.operationId === operation.id)
      .sort((left, right) => left.start - right.start),
    at: operation.start
  }))

  for (const span of spans) {
    if (!span.operationId || !operationIds.has(span.operationId)) {
      rows.push({
        id: `unknown-${span.id}`,
        operation: null,
        spans: [span],
        at: span.start
      })
    }
  }

  return rows.sort((left, right) => left.at - right.at)
}
