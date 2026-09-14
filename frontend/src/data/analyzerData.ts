import { inject, type InjectionKey } from 'vue'
import type { Alignment, Diagnosis, ModelRequestSample, OperationSample, SessionSummary, TimelineItem, TurnTimePart } from '../types'

/** Presentation snapshot; this is not the future backend wire format. */
export interface AnalyzerData {
  sessions: SessionSummary[]
  timelineItems: TimelineItem[]
  alignments: Alignment[]
  diagnoses: Diagnosis[]
  operationSamples: OperationSample[]
  modelRequests: ModelRequestSample[]
  turnTimeBreakdown: TurnTimePart[]
  trendData: { time: string; duration: number; ttft: number; tool: number }[]
  loading?: boolean
  error?: string
  refresh?: () => Promise<void>
}

export const analyzerDataKey: InjectionKey<AnalyzerData> = Symbol('analyzerData')

export function useAnalyzerData(): AnalyzerData {
  const data = inject(analyzerDataKey)
  if (!data) throw new Error('Analyzer data provider is required')
  return data
}
