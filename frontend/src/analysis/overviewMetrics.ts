import type { ModelRequestSample, OperationSample, SessionSummary } from '../types'
import { percentile } from './anomalyContribution'

export function calculateOverviewMetrics(
  turns: readonly SessionSummary[],
  requests: readonly ModelRequestSample[],
  operations: readonly OperationSample[],
) {
  const turnIds = new Set(turns.map(turn => turn.turnId))
  const completed = turns.filter(turn => turn.status !== '运行中')
  const durations = completed.map(turn => turn.durationMs).filter(validDuration)
  const scopedRequests = requests.filter(request => turnIds.has(request.turnId))
  const validRequests = scopedRequests.filter(request => request.status === '成功' && request.hasVisibleText && validDuration(request.ttftMs))
  const toolOperations = operations.filter(operation => turnIds.has(operation.turnId) && operation.category === '工具执行')
  const validTools = toolOperations.filter(operation => validDuration(operation.durationMs))
  const failed = completed.filter(turn => turn.status === '失败').length

  return {
    turns: turns.length,
    sessions: new Set(turns.map(turn => turn.sessionId)).size,
    completed: completed.length,
    running: turns.length - completed.length,
    p95DurationMs: percentile(durations, .95),
    durationSamples: durations.length,
    p95TtftMs: percentile(validRequests.map(request => request.ttftMs!), .95),
    requestSamples: validRequests.length,
    requestTotal: scopedRequests.length,
    p95ToolMs: percentile(validTools.map(operation => operation.durationMs), .95),
    toolSamples: validTools.length,
    toolTotal: toolOperations.length,
    failed,
    failureRate: completed.length ? failed / completed.length * 100 : 0,
  }
}

function validDuration(value: number | undefined): value is number {
  return value != null && Number.isFinite(value) && value >= 0
}
