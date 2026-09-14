import { describe, expect, it } from 'vitest'
import { calculateOverviewMetrics } from '../src/analysis/overviewMetrics'
import { createMockAnalyzerData } from '../src/data/mockAdapter'

describe('总览筛选指标', () => {
  it('按筛选轮次计算请求级 TTFT、工具耗时和失败率', () => {
    const data = createMockAnalyzerData()
    const turns = data.sessions.filter(turn => ['turn-demo-001', 'turn-demo-004', 'turn-demo-008'].includes(turn.turnId))
    const result = calculateOverviewMetrics(turns, data.modelRequests, data.operationSamples)
    expect(result.turns).toBe(3)
    expect(result.completed).toBe(2)
    expect(result.failureRate).toBe(50)
    expect(result.requestTotal).toBe(4)
    expect(result.requestSamples).toBe(3)
    expect(result.p95TtftMs).toBeCloseTo(7970)
    expect(result.toolSamples).toBe(2)
  })

  it('空筛选范围不会产生 NaN 或借用范围外样本', () => {
    const data = createMockAnalyzerData()
    const result = calculateOverviewMetrics([], data.modelRequests, data.operationSamples)
    expect(result).toMatchObject({ turns: 0, sessions: 0, p95DurationMs: 0, p95TtftMs: 0, p95ToolMs: 0, failureRate: 0 })
  })
})
