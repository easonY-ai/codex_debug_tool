import type { AnalyzerData } from './analyzerData'
import * as fixtures from './mockData'

/** Each application/test owns its snapshot, so mutations never alter fixtures. */
export function createMockAnalyzerData(): AnalyzerData {
  return structuredClone({
    sessions: fixtures.sessions,
    timelineItems: fixtures.timelineItems,
    alignments: fixtures.alignments,
    diagnoses: fixtures.diagnoses,
    operationSamples: fixtures.operationSamples,
    modelRequests: fixtures.modelRequests,
    turnTimeBreakdown: fixtures.turnTimeBreakdown,
    trendData: fixtures.trendData,
  })
}
