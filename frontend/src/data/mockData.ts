import type { Alignment, Diagnosis, ModelRequestSample, OperationSample, SessionSummary, TimelineItem, TurnTimePart } from '../types'

export const sessions: SessionSummary[] = [
  { id: 'turn-demo-001', sessionId: 'session-demo-a12f', sessionTitle: '支付回调稳定性排查', turnId: 'turn-demo-001', title: '请定位支付回调测试偶发超时的原因，并运行相关测试', project: 'demo-checkout', model: 'gpt-5.6-sol', source: 'CLI', status: '成功', startedAt: '今天 14:32', durationMs: 78200, ttftMs: 8400, modelMs: 27400, toolMs: 42100, tokenUsage: 18420, completeness: '完整', bottleneck: '测试工具执行', diagnosis: '工具占总耗时 54%', confidence: '高' },
  { id: 'turn-demo-002', sessionId: 'session-demo-b37c', sessionTitle: '订单状态机代码阅读', turnId: 'turn-demo-002', title: '请解释订单状态机的主要实现和状态转换', project: 'demo-orders', model: 'gpt-5.6-sol', source: 'IDE', status: '成功', startedAt: '今天 13:48', durationMs: 63400, ttftMs: 28700, modelMs: 48100, toolMs: 6200, tokenUsage: 23150, completeness: '完整', bottleneck: '首 Token 等待', diagnosis: 'TTFT 高于近期 P95', confidence: '高' },
  { id: 'turn-demo-003', sessionId: 'session-demo-c84e', sessionTitle: '前后端测试检查', turnId: 'turn-demo-003', title: '并行运行前端和后端测试，汇总失败信息', project: 'demo-console', model: 'gpt-5.6-sol', source: 'Desktop', status: '成功', startedAt: '今天 12:17', durationMs: 51200, ttftMs: 6200, modelMs: 15800, toolMs: 33400, tokenUsage: 14780, completeness: '部分缺失', bottleneck: '同名工具关联歧义', diagnosis: '2 个 shell Span 无法精确对应', confidence: '中' },
  { id: 'turn-demo-004', sessionId: 'session-demo-d09a', sessionTitle: 'Web 构建修复', turnId: 'turn-demo-004', title: '更新依赖并修复当前构建错误', project: 'demo-web', model: 'gpt-5.6-sol', source: 'CLI', status: '失败', startedAt: '今天 11:26', durationMs: 44900, ttftMs: 5100, modelMs: 13200, toolMs: 28000, tokenUsage: 9650, completeness: '完整', bottleneck: '工具失败重试', diagnosis: 'npm install 连续失败 2 次', confidence: '高' },
  { id: 'turn-demo-005', sessionId: 'session-demo-e55b', sessionTitle: '数据库结构调整', turnId: 'turn-demo-005', title: '根据实体变更生成数据库迁移脚本', project: 'demo-data', model: 'gpt-5.6-sol', source: 'IDE', status: '成功', startedAt: '今天 10:04', durationMs: 39600, ttftMs: 4900, modelMs: 12600, toolMs: 22500, tokenUsage: 11920, completeness: '仅 JSONL', bottleneck: '等待工具审批', diagnosis: '约 18 秒无性能细分', confidence: '中' },
  { id: 'turn-demo-006', sessionId: 'session-demo-f21d', sessionTitle: '接口性能检查', turnId: 'turn-demo-006', title: '分析接口性能采样并指出慢请求', project: 'demo-api', model: 'gpt-5.6-sol', source: 'Desktop', status: '成功', startedAt: '昨天 18:42', durationMs: 32800, ttftMs: 7200, modelMs: 20900, toolMs: 7900, tokenUsage: 8320, completeness: '仅 OTel', bottleneck: '模型请求链路', diagnosis: '缺少消息与工具正文', confidence: '高' },
  { id: 'turn-demo-007', sessionId: 'session-demo-g73a', sessionTitle: '通知模块重构', turnId: 'turn-demo-007', title: '重构通知模块并保持现有测试通过', project: 'demo-notify', model: 'gpt-5.6-terra', source: 'CLI', status: '成功', startedAt: '昨天 17:11', durationMs: 24600, ttftMs: 3800, modelMs: 14100, toolMs: 7500, tokenUsage: 7240, completeness: '完整', bottleneck: '无明显瓶颈', diagnosis: '耗时分布正常', confidence: '高' },
  { id: 'turn-demo-008', sessionId: 'session-demo-h46c', sessionTitle: '配置差异检查', turnId: 'turn-demo-008', title: '检查开发环境与测试环境的配置差异', project: 'demo-config', model: 'gpt-5.6-terra', source: 'IDE', status: '运行中', startedAt: '今天 14:58', durationMs: 18600, ttftMs: 4100, modelMs: 9200, toolMs: 5300, tokenUsage: 4180, completeness: '部分缺失', bottleneck: '仍在执行', diagnosis: '等待后续事件', confidence: '低' },
]

export const timelineItems: TimelineItem[] = [
  { id: 'evt-user-1', layer: 'Agent 行为', lane: '用户', title: '用户问题', startMs: 0, durationMs: 900, status: 'info', source: 'JSONL', input: '请定位支付回调测试偶发超时的原因，并运行相关测试。', description: '执行轮次从这条用户问题开始。', raw: { timestamp: '2026-09-10T06:32:00.000Z', session_id: 'session-demo-a12f', turn_id: 'turn-demo-001', type: 'user_message', content: '请定位支付回调测试偶发超时的原因，并运行相关测试。', cwd: '/workspace/demo-project' } },
  { id: 'evt-model-1', layer: 'Agent 行为', lane: '模型', title: '分析代码与制定步骤', startMs: 900, durationMs: 10800, status: 'success', source: 'JSONL', input: '用户问题 + 当前会话上下文（18 条消息）', output: '先定位支付回调处理器和相关超时配置，再运行最小范围测试复现问题。', description: '模型产生的可见分析摘要，不包含隐藏思维链。', raw: { timestamp: '2026-09-10T06:32:00.900Z', session_id: 'session-demo-a12f', turn_id: 'turn-demo-001', type: 'reasoning_summary', summary: '定位回调处理器和超时配置，然后运行最小范围测试。', input_tokens: 4210 } },
  { id: 'evt-tool-1', layer: 'Agent 行为', lane: '工具', title: 'rg 搜索回调处理器', startMs: 11800, durationMs: 3300, status: 'success', source: 'JSONL', callId: 'call_demo_rg_1', input: 'rg "callback|timeout" /workspace/demo-project', output: 'src/payment/callback.ts:48: const RETRY_TIMEOUT = 10000\ntests/payment-callback.spec.ts:72: retryDelay: 10000', description: '在示例项目中搜索 callback 和 timeout 相关实现。', raw: { timestamp: '2026-09-10T06:32:11.800Z', session_id: 'session-demo-a12f', turn_id: 'turn-demo-001', type: 'tool_call_and_result', call_id: 'call_demo_rg_1', tool: 'shell', arguments: { command: 'rg "callback|timeout" /workspace/demo-project' }, result: { exit_code: 0, output: 'src/payment/callback.ts:48...\ntests/payment-callback.spec.ts:72...' } } },
  { id: 'evt-tool-2', layer: 'Agent 行为', lane: '工具', title: '运行支付回调测试', startMs: 16900, durationMs: 42100, status: 'warning', source: 'JSONL', callId: 'call_demo_test_7', input: 'npm test -- payment-callback', output: 'PASS tests/payment-callback.spec.ts (41.2 s)\n3 tests passed；其中 retry fallback 等待约 30 秒。', description: '测试运行时间显著偏长，但最终通过。', raw: { timestamp: '2026-09-10T06:32:16.900Z', session_id: 'session-demo-a12f', turn_id: 'turn-demo-001', type: 'tool_call_and_result', call_id: 'call_demo_test_7', tool: 'shell', arguments: { command: 'npm test -- payment-callback' }, result: { exit_code: 0, duration_ms: 42100, output: 'PASS tests/payment-callback.spec.ts (41.2 s)\n3 tests passed' } } },
  { id: 'evt-model-2', layer: 'Agent 行为', lane: '模型', title: '归纳测试输出', startMs: 59400, durationMs: 11900, status: 'success', source: 'JSONL', input: '测试输出：3 tests passed；retry fallback 等待约 30 秒。', output: '主要耗时集中在测试桩的重试退避，并非支付回调业务逻辑本身。', description: '分析测试日志并形成根因判断。', raw: { timestamp: '2026-09-10T06:32:59.400Z', session_id: 'session-demo-a12f', turn_id: 'turn-demo-001', type: 'assistant_message', content: '主要耗时集中在测试桩的重试退避。', output_tokens: 7680 } },
  { id: 'evt-final-1', layer: 'Agent 行为', lane: '模型', title: '最终输出', startMs: 71900, durationMs: 6100, status: 'success', source: 'JSONL', input: '代码搜索结果、测试输出和当前轮次上下文', output: '定位完成：超时来自测试桩 retry fallback 的固定等待。建议在测试环境注入零延迟退避策略，并保留一条覆盖真实退避的集成测试。', description: 'Codex 返回给用户的最终可见回复。', raw: { timestamp: '2026-09-10T06:33:11.900Z', session_id: 'session-demo-a12f', turn_id: 'turn-demo-001', type: 'final_response', content: '定位完成：超时来自测试桩 retry fallback 的固定等待。', output_tokens: 1840 } },
  { id: 'span-api-1', layer: 'OTel 性能', lane: 'API / 传输', title: 'responses.create', startMs: 300, durationMs: 11200, status: 'success', source: 'OTel Trace', traceId: 'trace_demo_a91', description: '首次模型请求的端到端耗时；当前无法可靠拆分网络与模型处理时间。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_api_01', http_status: 200, duration_ms: 11200 } },
  { id: 'span-ttft-1', layer: 'OTel 性能', lane: '推理', title: 'TTFT · 8.4s', startMs: 500, durationMs: 8400, status: 'warning', source: 'OTel Trace', traceId: 'trace_demo_a91', description: '从请求发出到收到首个输出 Token。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_ttft_01', metric: 'time_to_first_token_ms', value: 8400 } },
  { id: 'span-tool-1', layer: 'OTel 性能', lane: '工具 Span', title: 'shell · rg', startMs: 11950, durationMs: 3080, status: 'success', source: 'OTel Trace', traceId: 'trace_demo_a91', callId: 'call_demo_rg_1', description: '搜索工具执行 Span，携带与 JSONL 相同的 call_id。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_tool_02', 'codex.call_id': 'call_demo_rg_1' } },
  { id: 'span-approval-1', layer: 'OTel 性能', lane: '审批 / 等待', title: '等待审批', startMs: 15100, durationMs: 1700, status: 'info', source: 'OTel Trace', traceId: 'trace_demo_a91', description: '高权限测试命令等待用户确认。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_approval_03', decision: 'approved' } },
  { id: 'span-tool-2', layer: 'OTel 性能', lane: '工具 Span', title: 'shell · npm test', startMs: 17100, durationMs: 41800, status: 'warning', source: 'OTel Trace', traceId: 'trace_demo_a91', callId: 'call_demo_test_7', description: '支付回调测试执行，实际延长本轮完成时间 41.8 秒。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_tool_04', 'codex.call_id': 'call_demo_test_7', exit_code: 0 } },
  { id: 'span-api-2', layer: 'OTel 性能', lane: 'API / 传输', title: 'responses.create', startMs: 59200, durationMs: 18100, status: 'success', source: 'OTel Trace', traceId: 'trace_demo_a91', description: '携带工具结果的第二次端到端模型请求；不推测拆分网络与模型处理时间。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_api_05', http_status: 200, duration_ms: 18100 } },
  { id: 'span-infer-2', layer: 'OTel 性能', lane: '推理', title: '生成结论', startMs: 61500, durationMs: 9500, status: 'success', source: 'OTel Trace', traceId: 'trace_demo_a91', description: '模型生成分析结论与修复建议。', raw: { trace_id: 'trace_demo_a91', span_id: 'span_infer_06', output_tokens: 1840 } },
]

export const alignments: Alignment[] = [
  { id: 'align-1', agentEventId: 'evt-tool-1', performanceSpanId: 'span-tool-1', level: 'EXACT', evidence: 'JSONL call_id 与 Span 属性 codex.call_id 完全一致', timeDeltaMs: 150 },
  { id: 'align-2', agentEventId: 'evt-tool-2', performanceSpanId: 'span-tool-2', level: 'EXACT', evidence: '公共 call_id = call_demo_test_7', timeDeltaMs: 200 },
  { id: 'align-3', agentEventId: 'evt-model-1', performanceSpanId: 'span-api-1', level: 'BOUNDED', evidence: '确认属于同一会话和 Turn，无法对应单个模型事件', timeDeltaMs: 600 },
  { id: 'align-4', agentEventId: 'evt-model-2', performanceSpanId: 'span-api-2', level: 'INFERRED', evidence: '事件类型相符且时间窗口重叠；没有公共事件 ID', timeDeltaMs: 200 },
  { id: 'align-5', agentEventId: 'evt-final-1', level: 'UNMATCHED', evidence: '未找到包含可靠公共 ID 的独立性能 Span' },
]

export const diagnoses: Diagnosis[] = [
  { id: 'diag-1', severity: '主要瓶颈', title: '测试工具执行过长', detail: '支付回调测试耗时 41.8 秒，是本轮实际等待时间最长的阶段。', impact: '占本轮耗时 53.5%', confidence: '高', targetId: 'span-tool-2' },
  { id: 'diag-2', severity: '次要问题', title: '模型请求链路响应偏慢', detail: '该请求 TTFT 为 8.4 秒，高于近期请求 P50（4.2 秒）；当前无法判断是网络还是模型处理导致。', impact: '增加约 4.2 秒', confidence: '高', targetId: 'span-ttft-1' },
  { id: 'diag-3', severity: '提示', title: '存在一条推测关联', detail: '第二次模型请求与“归纳测试输出”仅按类型和时间窗口匹配。', impact: '不影响本轮总耗时', confidence: '中', targetId: 'span-api-2' },
]

export const trendData = [
  { time: '09:00', duration: 31, ttft: 5, tool: 12 },
  { time: '10:00', duration: 40, ttft: 5, tool: 23 },
  { time: '11:00', duration: 45, ttft: 5, tool: 28 },
  { time: '12:00', duration: 51, ttft: 6, tool: 33 },
  { time: '13:00', duration: 63, ttft: 29, tool: 6 },
  { time: '14:00', duration: 78, ttft: 8, tool: 42 },
]

export const modelRequests: ModelRequestSample[] = [
  { id: 'request-demo-01', turnId: 'turn-demo-001', ttftMs: 8400, status: '成功', hasVisibleText: true },
  { id: 'request-demo-02', turnId: 'turn-demo-001', ttftMs: 3900, status: '成功', hasVisibleText: true },
  { id: 'request-demo-03', turnId: 'turn-demo-002', ttftMs: 28700, status: '成功', hasVisibleText: true },
  { id: 'request-demo-04', turnId: 'turn-demo-002', status: '失败', hasVisibleText: false },
  { id: 'request-demo-05', turnId: 'turn-demo-003', ttftMs: 6200, status: '成功', hasVisibleText: true },
  { id: 'request-demo-06', turnId: 'turn-demo-004', status: '取消', hasVisibleText: false },
  { id: 'request-demo-07', turnId: 'turn-demo-005', status: '成功', hasVisibleText: false },
  { id: 'request-demo-08', turnId: 'turn-demo-006', ttftMs: 7200, status: '成功', hasVisibleText: true },
  { id: 'request-demo-09', turnId: 'turn-demo-007', ttftMs: 3800, status: '成功', hasVisibleText: true },
  { id: 'request-demo-10', turnId: 'turn-demo-008', ttftMs: 4100, status: '成功', hasVisibleText: true },
]

export const operationSamples: OperationSample[] = [
  { id: 'op-m-01', turnId: 'turn-demo-001', category: '模型请求', operationType: 'responses-http', durationMs: 20100 },
  { id: 'op-m-02', turnId: 'turn-demo-002', category: '模型请求', operationType: 'responses-http', durationMs: 2200 },
  { id: 'op-m-03', turnId: 'turn-demo-003', category: '模型请求', operationType: 'responses-http', durationMs: 1900 },
  { id: 'op-m-04', turnId: 'turn-demo-004', category: '模型请求', operationType: 'responses-http', durationMs: 2100 },
  { id: 'op-m-05', turnId: 'turn-demo-005', category: '模型请求', operationType: 'responses-http', durationMs: 18500 },
  { id: 'op-m-06', turnId: 'turn-demo-006', category: '模型请求', operationType: 'responses-http', durationMs: 2000 },
  { id: 'op-m-07', turnId: 'turn-demo-007', category: '模型请求', operationType: 'responses-http', durationMs: 2300 },
  { id: 'op-t-01', turnId: 'turn-demo-001', category: '工具执行', operationType: 'shell:test', durationMs: 4200 },
  { id: 'op-t-02', turnId: 'turn-demo-002', category: '工具执行', operationType: 'shell:test', durationMs: 4000 },
  { id: 'op-t-03', turnId: 'turn-demo-003', category: '工具执行', operationType: 'shell:test', durationMs: 4100 },
  { id: 'op-t-04', turnId: 'turn-demo-004', category: '工具执行', operationType: 'shell:test', durationMs: 4300 },
  { id: 'op-t-05', turnId: 'turn-demo-005', category: '工具执行', operationType: 'shell:test', durationMs: 4000 },
  { id: 'op-t-06', turnId: 'turn-demo-006', category: '工具执行', operationType: 'shell:test', durationMs: 4200 },
  { id: 'op-t-07', turnId: 'turn-demo-007', category: '工具执行', operationType: 'shell:test', durationMs: 4100 },
  { id: 'op-a-01', turnId: 'turn-demo-001', category: '审批等待', operationType: 'approval', durationMs: 2900 },
  { id: 'op-a-02', turnId: 'turn-demo-002', category: '审批等待', operationType: 'approval', durationMs: 500 },
  { id: 'op-a-03', turnId: 'turn-demo-003', category: '审批等待', operationType: 'approval', durationMs: 600 },
  { id: 'op-a-04', turnId: 'turn-demo-004', category: '审批等待', operationType: 'approval', durationMs: 550 },
  { id: 'op-a-05', turnId: 'turn-demo-005', category: '审批等待', operationType: 'approval', durationMs: 650 },
  { id: 'op-a-06', turnId: 'turn-demo-006', category: '审批等待', operationType: 'approval', durationMs: 500 },
  { id: 'op-a-07', turnId: 'turn-demo-007', category: '审批等待', operationType: 'approval', durationMs: 580 },
]

export const turnTimeBreakdown: TurnTimePart[] = [
  { category: '模型请求', durationMs: 29300, source: 'OTel' },
  { category: '工具执行', durationMs: 41800, source: 'OTel' },
  { category: '审批等待', durationMs: 1700, source: 'OTel' },
  { category: '本地处理', durationMs: 2400, source: 'JSONL 估算' },
  { category: '未归因', durationMs: 3000, source: '差额' },
]
