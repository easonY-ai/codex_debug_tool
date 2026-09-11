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
const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}s`
const inspectorOpen = ref(false)
const inspectedEvent = ref<TimelineItem>()

const events = [
  { time: '+0.0s', type: '用户', state: 'done', item: { id: 'live-user-1', layer: 'Agent 行为', lane: '用户', title: '用户问题', startMs: 0, durationMs: 400, status: 'info', source: 'JSONL', input: '检查开发环境与测试环境的配置差异，并指出可能影响回调测试的配置。', description: '当前执行轮次的用户问题。', raw: { timestamp: '2026-09-11T06:58:00.000Z', session_id: 'session-demo-h46c', turn_id: 'turn-demo-008', type: 'user_message', content: '检查开发环境与测试环境的配置差异。' } } as TimelineItem },
  { time: '+4.1s', type: '模型', state: 'done', item: { id: 'live-otel-1', layer: 'OTel 性能', lane: '推理', title: '收到首个可见文本增量', startMs: 0, durationMs: 4100, status: 'success', source: 'OTel Trace', description: '本次模型请求的可见文本 TTFT 为 4.1 秒。', raw: { trace_id: 'trace_demo_live_1', span_id: 'span_demo_ttft_1', event: 'response.output_text.delta', duration_ms: 4100 } } as TimelineItem },
  { time: '+7.8s', type: '工具', state: 'done', item: { id: 'live-tool-1', layer: 'Agent 行为', lane: '工具', title: '读取配置文件', startMs: 7800, durationMs: 2100, status: 'success', source: 'JSONL', input: '读取 /workspace/demo-project/config/dev.toml 和 test.toml', output: '发现 3 处差异：retry_delay、callback_timeout、mock_clock。', description: '读取两个虚构配置文件并生成差异摘要。', raw: { timestamp: '2026-09-11T06:58:07.800Z', session_id: 'session-demo-h46c', turn_id: 'turn-demo-008', type: 'tool_call_and_result', call_id: 'call_demo_config_1', tool: 'read_file', result: { differences: 3 } } } as TimelineItem },
  { time: '+13.3s', type: '模型', state: 'active', item: { id: 'live-model-1', layer: 'Agent 行为', lane: '模型', title: '比较环境配置', startMs: 13300, durationMs: 5300, status: 'warning', source: 'JSONL', input: '配置差异：retry_delay、callback_timeout、mock_clock', output: '生成中：正在判断三项差异对测试耗时的影响……', description: '正在进行的可见分析摘要。', raw: { timestamp: '2026-09-11T06:58:13.300Z', session_id: 'session-demo-h46c', turn_id: 'turn-demo-008', type: 'reasoning_summary_delta', status: 'in_progress' } } as TimelineItem },
  { time: '等待中', type: '输出', state: 'pending', item: { id: 'live-final-1', layer: 'Agent 行为', lane: '模型', title: '最终回复', startMs: 18600, durationMs: 0, status: 'info', source: 'JSONL', input: '等待当前分析和工具执行完成', description: '最终输出尚未生成。', raw: { session_id: 'session-demo-h46c', turn_id: 'turn-demo-008', type: 'final_response', status: 'pending' } } as TimelineItem },
]

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
          <div class="panel-heading"><div><h2>{{ selected.title }}</h2><p>{{ selected.id }}</p></div><span class="running-label"><el-icon class="is-loading"><Loading /></el-icon>{{ selected.status }}</span></div>
          <div class="live-metrics"><div><span>已运行</span><b>{{ seconds(selected.durationMs) }}</b></div><div><span>TTFT</span><b>{{ seconds(selected.ttftMs ?? 0) }}</b></div><div><span>Token</span><b>{{ (selected.tokenUsage / 1000).toFixed(1) }}k</b></div><div><span>数据</span><b>{{ selected.completeness }}</b></div></div>
          <div class="stage-flow"><span class="done"><i><VideoPlay /></i>请求</span><b /><span class="done"><i><Connection /></i>分析</span><b /><span class="active"><i><Loading /></i>工具</span><b /><span><i><Clock /></i>输出</span></div>
        </article>

        <article class="panel event-stream-panel">
          <div class="panel-heading"><div><h2>最新事件</h2><p>按事件自身时间排序 · 点击查看输入与输出</p></div><button class="text-button">暂停滚动</button></div>
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
