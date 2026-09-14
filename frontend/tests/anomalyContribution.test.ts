import { describe, expect, it } from 'vitest'
import { calculateAnomalyContribution, percentile } from '../src/analysis/anomalyContribution'
import type { OperationSample } from '../src/types'

function samples(durations: number[], category: OperationSample['category'] = '模型请求', operationType = 'http'): OperationSample[] {
  return durations.map((durationMs, i) => ({ id: `${category}-${i}`, turnId: `turn-${i}`, category, operationType, durationMs }))
}
function calculate(input: OperationSample[]) {
  return calculateAnomalyContribution(input, input.map(item => item.turnId))
}

describe('异常耗时贡献', () => {
  it('将模型抖动识别为异常，稳定的较慢工具不贡献异常耗时', () => {
    const result = calculate([...samples([2000, 2000, 2000, 2000, 20000]), ...samples([4000, 4000, 4000, 4000, 4000], '工具执行', 'shell:test')])
    expect(result[0]).toMatchObject({ anomalyMs: 18000, share: 100, affectedTurns: 1, eligibleSamples: 5 })
    expect(result[1]).toMatchObject({ anomalyMs: 0, share: 0 })
  })

  it.each([
    [5000, 6000, 1000], // both thresholds exactly met
    [5000, 5999, 0], // absolute threshold not met
    [10000, 11999, 0], // relative threshold not met
    [10000, 12000, 2000],
    [0, 1000, 1000],
  ])('基线 %i、耗时 %i 的异常耗时为 %i', (baseline, duration, expected) => {
    expect(calculate(samples([baseline, baseline, baseline, baseline, duration]))[0].anomalyMs).toBe(expected)
  })

  it('筛选后重新判断五样本门槛，不使用范围外基线', () => {
    const input = samples([2000, 2000, 2000, 2000, 20000])
    expect(calculateAnomalyContribution(input, ['turn-0', 'turn-1', 'turn-2', 'turn-4'])[0]).toMatchObject({ sampleCount: 4, eligibleSamples: 0, anomalyMs: 0 })
    expect(calculateAnomalyContribution(input, []).every(row => row.sampleCount === 0 && row.share === 0)).toBe(true)
  })

  it('类别、传输方式和规范化工具操作分别建立基线', () => {
    const result = calculate([
      ...samples([2000, 2000, 2000, 2000, 20000]),
      ...samples([4000, 4000, 4000, 4000, 4000], '工具执行', 'http'),
      ...samples([20000, 20000, 20000, 20000, 20000], '模型请求', 'websocket'),
      ...samples([10000, 10000, 10000, 10000, 10000], '工具执行', 'shell:build'),
    ])
    expect(result[0]).toMatchObject({ anomalyMs: 18000, eligibleSamples: 10 })
    expect(result[1]).toMatchObject({ anomalyMs: 0, eligibleSamples: 10 })
  })

  it('同一轮多次异常只累计一个受影响轮次，贡献率总和为 100%', () => {
    const input = [...samples([2000, 2000, 2000, 2000, 5000, 5000]), ...samples([4000, 4000, 4000, 4000, 6000], '工具执行')]
    input[5].turnId = input[4].turnId
    const result = calculate(input)
    expect(result[0]).toMatchObject({ anomalyMs: 6000, affectedTurns: 1, share: 75 })
    expect(result[1].share).toBe(25)
  })

  it('无效耗时不进入有效样本数或分位数', () => {
    expect(calculate(samples([2000, 2000, 2000, 2000, NaN, Infinity, -1]))[0]).toMatchObject({ sampleCount: 4, eligibleSamples: 0, p50: 2000 })
  })

  it('分位数插值不改变输入数组', () => {
    const values = [40, 10, 30, 20]
    expect(percentile(values, .5)).toBe(25)
    expect(percentile(values, .95)).toBeCloseTo(38.5)
    expect(percentile(values, .99)).toBeCloseTo(39.7)
    expect(values).toEqual([40, 10, 30, 20])
  })
})
