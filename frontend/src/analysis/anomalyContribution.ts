import type { AnomalyCategory, OperationSample } from '../types'

const categories: AnomalyCategory[] = ['模型请求', '工具执行', '审批等待', '本地处理']

// Linear interpolation, shared by all displayed quantiles.
export function percentile(values: readonly number[], p: number): number {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const index = (sorted.length - 1) * p
  const lower = Math.floor(index)
  const upper = Math.ceil(index)
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
}

/** turnIds must contain only completed turns in the current filter scope. */
export function calculateAnomalyContribution(samples: readonly OperationSample[], turnIds: readonly string[]) {
  const scope = new Set(turnIds)
  const selected = samples.filter(sample => scope.has(sample.turnId) && Number.isFinite(sample.durationMs) && sample.durationMs >= 0)
  const groups = new Map<string, OperationSample[]>()
  for (const sample of selected) {
    // operationType is normalized transport or tool-name + operation, never a command.
    const key = JSON.stringify([sample.category, sample.operationType])
    const group = groups.get(key) ?? []
    group.push(sample)
    groups.set(key, group)
  }
  const rows = categories.map(category => {
    const durations = selected.filter(sample => sample.category === category).map(sample => sample.durationMs)
    let anomalyMs = 0
    let eligibleSamples = 0
    const affectedTurns = new Set<string>()
    for (const group of groups.values()) {
      if (group[0]?.category !== category || group.length < 5) continue
      eligibleSamples += group.length
      const baseline = percentile(group.map(sample => sample.durationMs), .5)
      for (const sample of group) {
        const excess = sample.durationMs - baseline
        if (excess >= 1000 && sample.durationMs >= baseline * 1.2) {
          anomalyMs += excess
          affectedTurns.add(sample.turnId)
        }
      }
    }
    return { category, sampleCount: durations.length, eligibleSamples, anomalyMs, affectedTurns: affectedTurns.size,
      p50: percentile(durations, .5), p95: percentile(durations, .95), p99: percentile(durations, .99), max: Math.max(0, ...durations) }
  })
  const total = rows.reduce((sum, row) => sum + row.anomalyMs, 0)
  return rows.map(row => ({ ...row, share: total ? row.anomalyMs / total * 100 : 0 }))
}
