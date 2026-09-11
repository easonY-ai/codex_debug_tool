export type ClientSource = 'Desktop' | 'CLI' | 'IDE'
export type SessionStatus = '成功' | '失败' | '运行中'
export type Completeness = '完整' | '仅 JSONL' | '仅 OTel' | '部分缺失'
export type AlignmentLevel = 'EXACT' | 'BOUNDED' | 'INFERRED' | 'UNMATCHED'

export interface SessionSummary {
  id: string
  sessionId: string
  sessionTitle: string
  turnId: string
  title: string
  project: string
  model: string
  source: ClientSource
  status: SessionStatus
  startedAt: string
  durationMs: number
  ttftMs?: number
  modelMs?: number
  toolMs?: number
  tokenUsage: number
  completeness: Completeness
  bottleneck: string
  diagnosis: string
  confidence: '高' | '中' | '低'
}

export interface TimelineItem {
  id: string
  layer: 'Agent 行为' | 'OTel 性能'
  lane: string
  title: string
  startMs: number
  durationMs: number
  status: 'success' | 'warning' | 'danger' | 'info'
  source: 'JSONL' | 'OTel Trace'
  description: string
  input?: string
  output?: string
  raw: Record<string, unknown>
  callId?: string
  traceId?: string
}

export interface Alignment {
  id: string
  agentEventId: string
  performanceSpanId?: string
  level: AlignmentLevel
  evidence: string
  timeDeltaMs?: number
}

export interface Diagnosis {
  id: string
  severity: '主要瓶颈' | '次要问题' | '提示'
  title: string
  detail: string
  impact: string
  confidence: '高' | '中' | '低'
  targetId?: string
}

export type AnomalyCategory = '模型请求' | '工具执行' | '审批等待' | '本地处理'

export interface OperationSample {
  id: string
  turnId: string
  category: AnomalyCategory
  operationType: string
  durationMs: number
}

export interface TurnTimePart {
  category: '模型请求' | '工具执行' | '审批等待' | '本地处理' | '未归因'
  durationMs: number
  source: 'OTel' | 'JSONL 估算' | '差额'
}
