import assert from 'node:assert/strict'
import test from 'node:test'
import { transcriptDocuments, transcriptJsonl, transcriptRecord } from './transcriptFixtures.ts'

test('payment tool evidence contains its full call and result records', () => {
  const document = transcriptDocuments['turn-demo-101']
  const call = transcriptRecord(document, 'payment-call')
  const result = transcriptRecord(document, 'payment-output')
  assert.deepEqual((call?.payload as { input: unknown }).input, {
    command: 'npm test -- payment-callback',
    cwd: '/workspace/demo-project'
  })
  assert.equal((result?.payload as { call_id: string }).call_id, 'call-demo-payment')
  assert.equal(((result?.payload as { output: { exit_code: number } }).output).exit_code, 0)
})

test('complete Transcript is valid JSONL with metadata, call, result and final output', () => {
  const document = transcriptDocuments['turn-demo-102']
  const lines = transcriptJsonl(document).split('\n').map(line => JSON.parse(line))
  assert.equal(lines.length, document.records.length)
  assert.equal(lines[0].payload.session_id, 'session-demo-b2')
  assert.equal(lines[2].payload.call_id, 'call-demo-7')
  assert.equal(lines[3].payload.output.length, 2)
  assert.equal(lines[3].payload.output[1].exit_code, 7)
  assert.equal(lines.at(-1)?.payload.type, 'task_complete')
})
