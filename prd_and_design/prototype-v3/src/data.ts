export type EvidenceLevel = 'EXACT' | 'BOUNDED' | 'INFERRED' | 'UNMATCHED'
export type Source = 'OTel 执行' | 'Transcript 内容' | 'OTel 性能'

export interface Turn {
  id: string
  sessionId: string
  sessionTitle: string
  question: string
  client: 'Desktop' | 'CLI' | 'IDE'
  state: '成功' | '失败' | '中断' | '运行中' | '不完整'
  duration: number
  ttft: number | null
  completeness: number
  content: 'AVAILABLE' | 'PENDING' | 'MISSING' | 'CONFLICT'
  diagnosis: string
  when: string
}

export interface Node {
  id: string
  title: string
  source: Source
  lane: string
  start: number
  end: number
  state: string
  content?: string
  raw: Record<string, unknown>
  transcriptRecordIds?: string[]
  level?: EvidenceLevel
  evidence?: string
  parent?: string
}

export const turns: Turn[] = [
  { id: 'turn-demo-101', sessionId: 'session-demo-a1', sessionTitle: '支付回调测试', question: '定位支付回调测试偶发超时，并运行相关测试', client: 'Desktop', state: '成功', duration: 78, ttft: 4.1, completeness: 96, content: 'AVAILABLE', diagnosis: '工具执行异常贡献 31 秒', when: '今天 14:32' },
  { id: 'turn-demo-102', sessionId: 'session-demo-b2', sessionTitle: '并行检查', question: '并行检查前后端测试并汇总失败项', client: 'CLI', state: '成功', duration: 42, ttft: 2.3, completeness: 92, content: 'AVAILABLE', diagnosis: '2 个命令子执行，1 个失败', when: '今天 13:18' },
  { id: 'turn-demo-103', sessionId: 'session-demo-c3', sessionTitle: '迁移方案', question: '比较数据库迁移方案并给出风险', client: 'IDE', state: '运行中', duration: 26, ttft: 3.8, completeness: 68, content: 'PENDING', diagnosis: '内容等待补齐', when: '今天 12:47' },
  { id: 'turn-demo-104', sessionId: 'session-demo-d4', sessionTitle: '接口故障', question: '分析接口 401 错误并重试', client: 'Desktop', state: '失败', duration: 18, ttft: null, completeness: 68, content: 'MISSING', diagnosis: '模型请求失败', when: '今天 11:16' },
  { id: 'turn-demo-105', sessionId: 'session-demo-e5', sessionTitle: '代码检索', question: '检索配置入口并说明调用路径', client: 'CLI', state: '中断', duration: 12, ttft: 1.9, completeness: 74, content: 'CONFLICT', diagnosis: 'Transcript 身份冲突，未合并', when: '昨天 18:34' },
  { id: 'turn-demo-106', sessionId: 'session-demo-f6', sessionTitle: '依赖更新', question: '更新虚构依赖并运行检查', client: 'IDE', state: '不完整', duration: 33, ttft: null, completeness: 59, content: 'MISSING', diagnosis: '缺少终态，不推定成功', when: '昨天 16:08' }
]

const id = { conversation: 'session-demo-b2', turn: 'turn-demo-102', call: 'call-demo-7' }
const parallelTranscript = transcriptDocuments[id.turn]

export const traceNodes: Node[] = [
  { id: 'entry', title: '用户提交', source: 'OTel 执行', lane: '用户 / 轮次', start: 0, end: 0.7, state: '完成', level: 'EXACT', evidence: 'conversation.id 与 session_meta.session_id、turn.id 与 TranscriptItem.turn_id 相同。', content: '并行检查前后端测试并汇总失败项', raw: { event: 'codex.user_prompt', 'conversation.id': id.conversation, 'turn.id': id.turn }, transcriptRecordIds: ['parallel-user'] },
  { id: 'api-1', title: '模型请求', source: 'OTel 性能', lane: 'API / TTFT', start: 0.7, end: 7.4, state: '完成', content: 'TTFT 2.3 秒；客户端端到端 6.7 秒', raw: { event: 'codex.api_request', 'turn.id': id.turn, duration_ms: 6700, ttft_ms: 2300 } },
  { id: 'decision', title: '工具批准', source: 'OTel 执行', lane: '决策', start: 7.4, end: 7.4, state: '已批准', content: '来源：用户。等待耗时未知。', raw: { event: 'codex.tool_decision', decision: 'approved', source: 'user', 'turn.id': id.turn } },
  { id: 'tool-parent', title: '模型 Tool Call · shell', source: 'OTel 执行', lane: '工具调用', start: 10, end: 31, state: '已返回', level: 'EXACT', evidence: 'Codex 0.154.0 本地模型 Call ID 同值：OTel call_id = Transcript custom_tool_call.call_id。', content: '1 个模型调用，2 个并行命令子执行。父耗时 21 秒，未对子执行耗时求和。', raw: { event: 'codex.tool_result', call_id: id.call, success: true, 'turn.id': id.turn }, transcriptRecordIds: ['parallel-call', 'parallel-output'] },
  { id: 'cmd-front', parent: 'tool-parent', title: '子执行 · 前端测试', source: 'Transcript 内容', lane: '前端子执行', start: 11, end: 28, state: '成功', level: 'BOUNDED', evidence: '属于同一个已精确关联的模型 Tool Call；子执行身份由 Transcript 拥有。', content: 'npm test -- frontend · exit_code 0', raw: transcriptRecord(parallelTranscript, 'parallel-output')! },
  { id: 'cmd-back', parent: 'tool-parent', title: '子执行 · 后端测试', source: 'Transcript 内容', lane: '后端子执行', start: 11.5, end: 31, state: '失败', level: 'BOUNDED', evidence: 'OTel tool_result.success=true 表示协议调用已返回；本地命令结果以 Transcript status/exit_code 为准。', content: 'mvn test · exit_code 7', raw: transcriptRecord(parallelTranscript, 'parallel-output')! },
  { id: 'api-2', title: '模型汇总请求', source: 'OTel 性能', lane: 'API / TTFT', start: 31.3, end: 40, state: '完成', content: '客户端端到端 8.7 秒', raw: { event: 'codex.api_request', 'turn.id': id.turn, duration_ms: 8700 } },
  { id: 'final', title: '最终回复', source: 'Transcript 内容', lane: '可见内容', start: 40, end: 41.2, state: '已补齐', level: 'EXACT', evidence: '同一 Session/Turn 身份；最终可见文本由 Transcript 拥有。', content: '前端测试通过；后端测试退出码 7，失败项位于合成测试模块。', raw: transcriptRecord(parallelTranscript, 'parallel-final')! },
  { id: 'terminal', title: '轮次完成', source: 'OTel 执行', lane: '用户 / 轮次', start: 41.2, end: 42, state: '成功', content: 'OTel 终态为完成；命令失败仍在子执行层独立展示。', raw: { event: 'response.completed', 'turn.id': id.turn }, transcriptRecordIds: ['parallel-complete'] }
]

export const extraNodes: Record<string, Node[]> = {
  'turn-demo-101': [
    { id: 'entry-a', title: '用户提交', source: 'OTel 执行', lane: '用户 / 轮次', start: 0, end: 1, state: '完成', content: turns[0].question, raw: { event: 'codex.user_prompt', 'turn.id': turns[0].id }, transcriptRecordIds: ['payment-user'] },
    { id: 'api-a', title: '模型请求', source: 'OTel 性能', lane: 'API / TTFT', start: 1, end: 16, state: '完成', content: 'TTFT 4.1 秒', raw: { event: 'codex.api_request', ttft_ms: 4100 } },
    { id: 'tool-a', title: '运行支付回调测试', source: 'OTel 执行', lane: '工具调用', start: 17, end: 59, state: '已返回', level: 'EXACT', evidence: '0.154.0 本地模型 Call ID 已验证相同。', content: 'npm test -- payment-callback', raw: { event: 'codex.tool_result', call_id: 'call-demo-payment' }, transcriptRecordIds: ['payment-call', 'payment-output'] },
    { id: 'api-b', title: '最终模型请求', source: 'OTel 性能', lane: 'API / TTFT', start: 59, end: 77, state: '完成', raw: { event: 'codex.api_request', duration_ms: 18000 } },
    { id: 'done-a', title: '轮次完成', source: 'OTel 执行', lane: '用户 / 轮次', start: 77, end: 78, state: '成功', content: '可见最终回复来自 Transcript。', raw: { event: 'response.completed' }, transcriptRecordIds: ['payment-final', 'payment-complete'] }
  ]
}

export const transcriptOnly = { path: '/workspace/demo-project/sessions/inspection-only.jsonl', sessionId: 'session-demo-only', state: 'INSPECTION_ONLY', raw: { type: 'session_meta', payload: { session_id: 'session-demo-only' } } }
import { transcriptDocuments, transcriptRecord } from './transcriptFixtures'
