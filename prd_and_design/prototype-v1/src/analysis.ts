export interface OperationSample {
  turnId: string
  model: number
  tool: number
}

export function percentile(values: number[], fraction: number): number | null {
  const valid = values.filter(value => Number.isFinite(value) && value >= 0)
  if (!valid.length) return null
  const sorted = [...valid].sort((a, b) => a - b)
  const position = (sorted.length - 1) * fraction
  const lower = Math.floor(position)
  return sorted[lower] + (sorted[Math.ceil(position)] - sorted[lower]) * (position - lower)
}

export function anomalyContribution(samples: OperationSample[], selectedTurnIds: string[]) {
  const included = samples.filter(sample => selectedTurnIds.includes(sample.turnId))
  const rows = ([{ key: 'model', label: '模型请求' }, { key: 'tool', label: '工具执行' }] as const).map(category => {
    const values = included.map(sample => sample[category.key]).filter(value => Number.isFinite(value) && value >= 0)
    const baseline = percentile(values, .5) ?? 0
    const excess = values.length < 5 ? 0 : values.reduce((sum, value) => {
      return sum + (value - baseline >= 1 && value >= baseline * 1.2 ? value - baseline : 0)
    }, 0)
    return { ...category, count: values.length, baseline, excess }
  })
  const total = rows.reduce((sum, row) => sum + row.excess, 0)
  return rows.map(row => ({ ...row, share: total ? Math.round(row.excess / total * 100) : 0 }))
}

export function conservedTimeParts(total: number, model: number, tool: number, local: number) {
  const rounded = [model, tool, local].map(value => Math.round(value * 10) / 10)
  const unknown = Math.round((total - rounded[0] - rounded[1] - rounded[2]) * 10) / 10
  return [...rounded, unknown]
}

export function completenessPercentage(parts: readonly number[]) {
  if (parts.length !== 4 || parts.some(value => !Number.isFinite(value) || value < 0 || value > 100)) {
    throw new Error('Four valid coverage values are required')
  }
  return parts.reduce((sum, value) => sum + value, 0) / 4
}
