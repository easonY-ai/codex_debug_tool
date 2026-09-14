<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Clock, Connection, Loading, VideoPlay } from '@element-plus/icons-vue'
import { useAnalyzerData } from '../data/analyzerData'
import EventInspectorDrawer from './EventInspectorDrawer.vue'
import type { TimelineItem } from '../types'

const { sessions } = useAnalyzerData()

const elapsed = ref(0)
const selectedId = ref('')
const timer = window.setInterval(() => { elapsed.value += 1000 }, 1000)
onBeforeUnmount(() => window.clearInterval(timer))

const liveSessions = computed(() => sessions.slice(0, 20).map((item) => ({ ...item,
  durationMs: item.status === '运行中' ? item.durationMs + elapsed.value : item.durationMs,
  stage: item.status === '运行中' ? '等待最新 Hook/OTel 事件' : '已生成最终状态',
  progress: item.status === '运行中' ? 50 : 100, lastEvent: '以服务端事件时间为准', events: 0 })))
const selected = computed(() => liveSessions.value.find((item) => item.id === selectedId.value) ?? liveSessions.value[0])
const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}s`
const inspectorOpen = ref(false)
const inspectedEvent = ref<TimelineItem>()

const events: Array<{ time: string; type: string; state: string; item: TimelineItem }> = []

function inspect(item: TimelineItem) { inspectedEvent.value = item; inspectorOpen.value = true }
</script>

<template>
  <main class="page-wrap live-page">
    <section class="hero-row compact-hero">
      <div><div class="eyebrow">LIVE ACTIVITY</div><h1>实时会话</h1><p>观察正在执行的任务、最新事件和当前性能阶段。</p></div>
      <div class="live-pill"><span class="live-dot pulse" />{{ liveSessions.filter(item => item.status === '运行中').length }} 个轮次执行中</div>
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

      <div v-if="selected" class="live-detail-stack">
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
      </div><el-empty v-else description="尚未收到 Hook 轮次事件" />
    </section>
    <EventInspectorDrawer v-model="inspectorOpen" :item="inspectedEvent" />
  </main>
</template>
