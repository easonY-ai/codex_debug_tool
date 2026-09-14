import { describe, expect, it, vi } from 'vitest'
import { createApiAnalyzerData, mapTurn } from '../src/data/apiAdapter'

describe('API adapter', () => {
  it('maps missing performance fields honestly', () => {
    const value = mapTurn({ session_id:'session-test', turn_id:'turn-test', state:'RUNNING' })
    expect(value.source).toBe('未知'); expect(value.ttftMs).toBeUndefined(); expect(value.title).toContain('等待')
  })
  it('loads real session endpoint', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({items:[{session_id:'s',turn_id:'t',state:'COMPLETED',title:'真实测试'}]}), {status:200}))
    const data = createApiAnalyzerData(fetcher as typeof fetch); await data.refresh?.()
    expect(fetcher).toHaveBeenCalledWith('/api/sessions?limit=200'); expect(data.sessions[0].title).toBe('真实测试')
  })
})
