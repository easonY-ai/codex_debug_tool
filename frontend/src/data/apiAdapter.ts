import { reactive } from 'vue'
import type { AnalyzerData } from './analyzerData'
import type { SessionSummary } from '../types'

type WireTurn = { session_id: string; turn_id: string; title?: string; model?: string; cwd?: string; state: string; started_at?: number; duration_ms?: number; transcript_path?: string; tool_ms?:number }

export function mapTurn(item: WireTurn): SessionSummary {
  const status = item.state === 'RUNNING' || item.state === 'UNKNOWN' ? '运行中' : item.state === 'COMPLETED' ? '成功' : '失败'
  return { id: item.turn_id, sessionId: item.session_id, sessionTitle: item.session_id, turnId: item.turn_id,
    title: item.title || '等待用户问题内容补齐', project: item.cwd || '未知', model: item.model || '未知', source: '未知', status,
    startedAt: item.started_at ? new Date(item.started_at).toLocaleString() : '时间未知', durationMs: item.duration_ms || 0,
    toolMs:item.tool_ms, tokenUsage: 0, completeness:'部分缺失', bottleneck: item.tool_ms?'工具执行':'等待性能数据',
    diagnosis: 'OTel 或 transcript 数据尚未补齐', confidence: '低' }
}

export function createApiAnalyzerData(fetcher: typeof fetch = fetch): AnalyzerData {
  const data = reactive<AnalyzerData>({ sessions: [], timelineItems: [], alignments: [], diagnoses: [], operationSamples: [],
    modelRequests: [], turnTimeBreakdown: [], trendData: [], loading: true })
  data.refresh = async () => {
    data.loading = true; data.error = undefined
    try { const response = await fetcher('/api/sessions?limit=200'); if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const body = await response.json(); const wire:WireTurn[]=body.items;data.sessions.splice(0, data.sessions.length, ...wire.map(mapTurn))
      data.operationSamples.splice(0,data.operationSamples.length,...wire.filter(i=>i.tool_ms!=null).map(i=>({id:`tool-${i.turn_id}`,turnId:i.turn_id,category:'工具执行' as const,operationType:'hook-tool-total',durationMs:Number(i.tool_ms)})))
      data.trendData.splice(0,data.trendData.length,...wire.filter(i=>i.started_at).map(i=>({time:new Date(i.started_at!).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}),duration:Number(i.duration_ms??0)/1000,ttft:0,tool:Number(i.tool_ms??0)/1000})))
    } catch (error) { data.error = error instanceof Error ? error.message : '加载失败' }
    finally { data.loading = false }
  }
  void data.refresh()
  if (typeof EventSource !== 'undefined') { const stream=new EventSource('/api/events'); stream.addEventListener('hook',()=>void data.refresh?.()) }
  return data
}
