<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Clock, Connection, Loading, VideoPlay } from '@element-plus/icons-vue'
import { sessions } from '../mockData'
import EventInspectorDrawer from './EventInspectorDrawer.vue'
import type { TimelineItem } from '../types'

const elapsed = ref(18600)
const selectedId = ref('turn-demo-008')
const timer = window.setInterval(() => { elapsed.value += 1000 }, 1000)
onBeforeUnmount(() => window.clearInterval(timer))

const liveSessions = computed(() => [
  { ...sessions[7], durationMs: elapsed.value, stage: '读取配置差异', progress: 67, lastEvent: '刚刚', events: 14 },
  { ...sessions[2], status: '运行中' as const, durationMs: 42300, stage: '等待并行测试结果', progress: 81, lastEvent: '3 秒前', events: 27 },
  { ...sessions[0], stage: '已生成最终回复', progress: 100, lastEvent: '2 分钟前', events: 36 },
])
const selected = computed(() => liveSessions.value.find((item) => item.id === selectedId.value) ?? liveSessions.value[0])
const contentComplete = computed(() => selectedId.value === 'turn-demo-008' || selected.value.completeness !== '缺少内容')
const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}s`
const inspectorOpen = ref(false)
const inspectedEvent = ref<TimelineItem>()

const primaryEvents = [
  { time: '+0.0s', type: '用户', state: 'done', item: { id: 'live-user-1', layer: 'Hook 行为', lane: '用户', title: '用户问题', startMs: 0, durationMs: 400, status: 'info', source: 'Hook + JSONL', input: '检查开发环境与测试环境的配置差异，并指出可能影响回调测试的配置。', contentStatus: '已补齐', timingPrecision: 'Hook 边界估算', description: 'Hook 建立轮次入口，JSONL 已补齐用户正文。', hookRaw: { event: 'UserPromptSubmit', session_id: 'session-demo-h46c', turn_id: 'turn-demo-008', transcript_path: '/workspace/demo-project/sessions/demo-008.jsonl' }, raw: { timestamp: '2026-09-11T06:58:00.000Z', type: 'event_msg', payload: { type: 'user_message', message: '检查开发环境与测试环境的配置差异。' } } } as TimelineItem },
  { time: '+4.1s', type: '模型', state: 'done', item: { id: 'live-otel-1', layer: 'OTel 性能', lane: '推理', title: '收到首个可见文本增量', startMs: 0, durationMs: 4100, status: 'success', source: 'OTel Trace', description: '本次模型请求的可见文本 TTFT 为 4.1 秒。', raw: { trace_id: 'trace_demo_live_1', span_id: 'span_demo_ttft_1', event: 'response.output_text.delta', duration_ms: 4100 } } as TimelineItem },
  { time: '+7.8s', type: '工具', state: 'done', item: { id: 'live-tool-1', layer: 'Hook 行为', lane: '工具', title: '读取配置文件', startMs: 7800, durationMs: 2100, status: 'success', source: 'Hook + JSONL', input: '读取 /workspace/demo-project/config/dev.toml 和 test.toml', output: '发现 3 处差异：retry_delay、callback_timeout、mock_clock。', contentStatus: '已补齐', timingPrecision: 'OTel 精确', toolUseId: 'tooluse_demo_config_1', description: 'Hook 建立工具调用，JSONL 补齐参数和结果，OTel 补齐耗时。', hookRaw: { event: 'PostToolUse', session_id: 'session-demo-h46c', turn_id: 'turn-demo-008', tool_use_id: 'tooluse_demo_config_1' }, raw: { timestamp: '2026-09-11T06:58:07.800Z', type: 'response_item', payload: { type: 'custom_tool_call_output', call_id: 'call_demo_config_1' } } } as TimelineItem },
  { time: '+13.3s', type: '模型', state: 'active', item: { id: 'live-model-1', layer: 'Hook 行为', lane: '模型', title: '比较环境配置', startMs: 13300, durationMs: 5300, status: 'warning', source: 'Hook + JSONL', input: '配置差异：retry_delay、callback_timeout、mock_clock', output: '生成中：正在判断三项差异对测试耗时的影响……', contentStatus: '已补齐', timingPrecision: 'Hook 边界估算', description: 'Hook 表明轮次仍在运行，JSONL 已补齐当前可见摘要。', hookRaw: { event: 'UserPromptSubmit', session_id: 'session-demo-h46c', turn_id: 'turn-demo-008' }, raw: { timestamp: '2026-09-11T06:58:13.300Z', type: 'response_item', payload: { type: 'message', phase: 'commentary' } } } as TimelineItem },
  { time: '等待中', type: '输出', state: 'pending', item: { id: 'live-final-1', layer: 'Hook 行为', lane: '模型', title: '最终回复', startMs: 18600, durationMs: 0, status: 'info', source: 'Hook', contentStatus: '等待补齐', timingPrecision: 'Hook 边界估算', description: '内部推导状态：已收到轮次开始 Hook，尚未收到终止 Hook；最终内容尚未生成。', hookRaw: { derived_state: 'RUNNING', evidence: { turn_started: true, terminal_hook_received: false }, session_id: 'session-demo-h46c', turn_id: 'turn-demo-008' }, raw: {} } as TimelineItem },
]
const events = computed(() => selectedId.value === 'turn-demo-008' ? primaryEvents : [
  { time: '+0.0s', type: 'Hook', state: 'done', item: { id: `live-${selectedId.value}-start`, layer: 'Hook 行为', lane: '用户', title: '轮次开始', startMs: 0, durationMs: 200, status: 'info', source: contentComplete.value ? 'Hook + JSONL' : 'Hook', contentStatus: contentComplete.value ? '已补齐' : '等待补齐', timingPrecision: 'Hook 边界估算', description: contentComplete.value ? '该会话有独立的 Hook 骨架，JSONL 内容已经补齐。' : '该会话有独立的 Hook 骨架，正文仍等待 JSONL 补齐。', hookRaw: { event: 'UserPromptSubmit', session_id: selected.value.sessionId, turn_id: selected.value.turnId }, raw: contentComplete.value ? { type: 'event_msg', payload: { type: 'user_message', message: selected.value.title } } : {} } as TimelineItem },
])

function inspect(item: TimelineItem) { inspectedEvent.value = item; inspectorOpen.value = true }
</script>

<template>
  <main class="page-wrap live-page">
    <section class="hero-row compact-hero">
      <div><div class="eyebrow">LIVE ACTIVITY</div><h1>实时会话</h1><p>观察正在执行的任务、最新事件和当前性能阶段。</p></div>
      <div class="live-pill"><span class="live-dot pulse" />2 个会话执行中</div>
    </section>

    <section class="live-layout">
      <div class="panel live-list-panel">
        <div class="panel-heading"><div><h2>当前活动</h2><p>自动刷新 · 完全虚构的演示数据</p></div><el-tag type="success" effect="plain">连接正常</el-tag></div>
        <button v-for="item in liveSessions" :key="item.id" :class="['live-session-card', { active: item.id === selectedId }]" @click="selectedId = item.id">
          <span :class="['session-state-dot', item.status === '运行中' ? 'running' : 'finished']" />
          <span class="live-card-main"><span><b>{{ item.title }}</b><el-tag v-if="item.status === '运行中'" size="small">运行中</el-tag><el-tag v-else type="success" size="small">已完成</el-tag></span><small>{{ item.sessionTitle }} · {{ item.sessionId }} · {{ item.source }}</small><em>{{ item.stage }}</em><el-progress :percentage="item.progress" :show-text="false" :status="item.progress === 100 ? 'success' : undefined" /></span>
          <span class="live-card-meta"><b>{{ seconds(item.durationMs) }}</b><small>{{ item.lastEvent }}</small><small>{{ item.events }} 个事件</small></span>
        </button>
      </div>

      <div class="live-detail-stack">
        <article class="panel live-focus-panel">
          <div class="panel-heading"><div><h2>{{ selected.title }}</h2><p>{{ selected.id }}</p></div><span class="running-label"><el-icon v-if="selected.status === '运行中'" class="is-loading"><Loading /></el-icon>{{ selected.status }}</span></div>
          <div class="live-metrics"><div><span>{{ selected.status === '运行中' ? '已运行' : '总耗时' }}</span><b>{{ seconds(selected.durationMs) }}</b></div><div><span>TTFT · OTel</span><b>{{ selected.ttftMs == null ? '未知' : seconds(selected.ttftMs) }}</b></div><div><span>Token</span><b>{{ (selected.tokenUsage / 1000).toFixed(1) }}k</b></div><div><span>数据补齐</span><b>{{ selected.completeness }}</b></div></div>
          <div class="stage-flow"><span class="done"><i><VideoPlay /></i>Hook 入口</span><b /><span :class="contentComplete ? 'done' : 'active'"><i><Connection /></i>{{ contentComplete ? '内容已补齐' : '等待内容' }}</span><b /><span :class="selected.status === '运行中' ? 'active' : 'done'"><i><Loading /></i>执行</span><b /><span :class="selected.status === '成功' ? 'done' : ''"><i><Clock /></i>{{ selected.status === '成功' ? '已结束' : '等待输出' }}</span></div>
        </article>

        <article class="panel event-stream-panel">
          <div class="panel-heading"><div><h2>Hook 执行事件</h2><p>Hook 建立节点；JSONL 和 OTel 到达后在同一节点补齐</p></div><button class="text-button">暂停滚动</button></div>
          <div class="event-stream">
            <button v-for="event in events" :key="event.item.id" :class="['stream-event', event.state]" @click="inspect(event.item)">
              <span class="stream-time">{{ event.time }}</span><i /><span><em>{{ event.type }} · {{ event.item.source }}</em><b>{{ event.item.title }}</b><small>{{ event.item.description }}</small></span><strong>查看</strong>
            </button>
          </div>
        </article>
      </div>
    </section>
    <EventInspectorDrawer v-model="inspectorOpen" :item="inspectedEvent" />
  </main>
</template>
