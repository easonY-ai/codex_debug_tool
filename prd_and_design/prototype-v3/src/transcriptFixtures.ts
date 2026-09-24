export interface TranscriptRecord {
  id: string
  raw: Record<string, unknown>
}

export interface TranscriptDocument {
  path: string
  records: TranscriptRecord[]
}

const paymentTurn = 'turn-demo-101'
const parallelTurn = 'turn-demo-102'

export const transcriptDocuments: Record<string, TranscriptDocument> = {
  [paymentTurn]: {
    path: '/workspace/demo-project/sessions/payment-demo.jsonl',
    records: [
      {
        id: 'payment-meta',
        raw: {
          timestamp: '2026-09-10T06:32:00.000Z',
          type: 'session_meta',
          payload: { session_id: 'session-demo-a1', cwd: '/workspace/demo-project', source: 'desktop' }
        }
      },
      {
        id: 'payment-user',
        raw: {
          timestamp: '2026-09-10T06:32:00.100Z',
          type: 'event_msg',
          payload: {
            type: 'user_message',
            message: '定位支付回调测试偶发超时，并运行相关测试',
            internal_chat_message_metadata_passthrough: { turn_id: paymentTurn }
          }
        }
      },
      {
        id: 'payment-call',
        raw: {
          timestamp: '2026-09-10T06:32:17.000Z',
          type: 'response_item',
          payload: {
            type: 'custom_tool_call',
            call_id: 'call-demo-payment',
            name: 'functions.shell',
            input: { command: 'npm test -- payment-callback', cwd: '/workspace/demo-project' },
            internal_chat_message_metadata_passthrough: { turn_id: paymentTurn }
          }
        }
      },
      {
        id: 'payment-output',
        raw: {
          timestamp: '2026-09-10T06:32:59.000Z',
          type: 'response_item',
          payload: {
            type: 'custom_tool_call_output',
            call_id: 'call-demo-payment',
            output: {
              type: 'CommandExecution',
              id: 'exec-demo-payment',
              command: 'npm test -- payment-callback',
              status: 'completed',
              exit_code: 0,
              stdout: 'PASS tests/payment-callback.spec.ts\n3 tests passed',
              stderr: ''
            },
            internal_chat_message_metadata_passthrough: { turn_id: paymentTurn }
          }
        }
      },
      {
        id: 'payment-final',
        raw: {
          timestamp: '2026-09-10T06:33:18.000Z',
          type: 'response_item',
          payload: {
            type: 'message',
            role: 'assistant',
            phase: 'final_answer',
            content: [{ type: 'output_text', text: '测试通过。耗时集中在虚构测试桩的重试退避。' }],
            internal_chat_message_metadata_passthrough: { turn_id: paymentTurn }
          }
        }
      },
      {
        id: 'payment-complete',
        raw: {
          timestamp: '2026-09-10T06:33:18.100Z',
          type: 'event_msg',
          payload: {
            type: 'task_complete',
            turn_id: paymentTurn,
            last_agent_message: '测试通过。耗时集中在虚构测试桩的重试退避。'
          }
        }
      }
    ]
  },
  [parallelTurn]: {
    path: '/workspace/demo-project/sessions/parallel-demo.jsonl',
    records: [
      {
        id: 'parallel-meta',
        raw: {
          timestamp: '2026-09-10T05:18:00.000Z',
          type: 'session_meta',
          payload: { session_id: 'session-demo-b2', cwd: '/workspace/demo-project', source: 'cli' }
        }
      },
      {
        id: 'parallel-user',
        raw: {
          timestamp: '2026-09-10T05:18:00.100Z',
          type: 'event_msg',
          payload: {
            type: 'user_message',
            message: '并行检查前后端测试并汇总失败项',
            internal_chat_message_metadata_passthrough: { turn_id: parallelTurn }
          }
        }
      },
      {
        id: 'parallel-call',
        raw: {
          timestamp: '2026-09-10T05:18:10.000Z',
          type: 'response_item',
          payload: {
            type: 'custom_tool_call',
            call_id: 'call-demo-7',
            name: 'functions.shell',
            input: {
              commands: [
                { id: 'exec-demo-front', command: 'npm test -- frontend', cwd: '/workspace/demo-project' },
                { id: 'exec-demo-back', command: 'mvn test', cwd: '/workspace/demo-project' }
              ]
            },
            internal_chat_message_metadata_passthrough: { turn_id: parallelTurn }
          }
        }
      },
      {
        id: 'parallel-output',
        raw: {
          timestamp: '2026-09-10T05:18:31.000Z',
          type: 'response_item',
          payload: {
            type: 'custom_tool_call_output',
            call_id: 'call-demo-7',
            output: [
              { type: 'CommandExecution', id: 'exec-demo-front', command: 'npm test -- frontend', status: 'completed', exit_code: 0, stdout: '19 tests passed', stderr: '' },
              { type: 'CommandExecution', id: 'exec-demo-back', command: 'mvn test', status: 'failed', exit_code: 7, stdout: '27 tests run', stderr: '1 synthetic test failed' }
            ],
            internal_chat_message_metadata_passthrough: { turn_id: parallelTurn }
          }
        }
      },
      {
        id: 'parallel-final',
        raw: {
          timestamp: '2026-09-10T05:18:41.200Z',
          type: 'response_item',
          payload: {
            type: 'message',
            role: 'assistant',
            phase: 'final_answer',
            content: [{ type: 'output_text', text: '前端测试通过；后端测试退出码 7，失败项位于合成测试模块。' }],
            internal_chat_message_metadata_passthrough: { turn_id: parallelTurn }
          }
        }
      },
      {
        id: 'parallel-complete',
        raw: {
          timestamp: '2026-09-10T05:18:42.000Z',
          type: 'event_msg',
          payload: {
            type: 'task_complete',
            turn_id: parallelTurn,
            last_agent_message: '前端测试通过；后端测试退出码 7，失败项位于合成测试模块。'
          }
        }
      }
    ]
  }
}

export function transcriptRecord(document: TranscriptDocument, recordId: string) {
  return document.records.find(record => record.id === recordId)?.raw
}

export function transcriptJsonl(document: TranscriptDocument) {
  return document.records.map(record => JSON.stringify(record.raw)).join('\n')
}
