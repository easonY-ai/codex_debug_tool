<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Filter, Search } from '@element-plus/icons-vue'
import { useAnalyzerData } from '../data/analyzerData'
import type { SessionSummary } from '../types'
import TrendChart from './TrendChart.vue'
import MetricHelp from './MetricHelp.vue'
import AnomalyContributionPanel from './AnomalyContributionPanel.vue'
import { calculateOverviewMetrics } from '../analysis/overviewMetrics'

const { sessions, modelRequests, operationSamples } = useAnalyzerData()

const emit = defineEmits<{ open: [session: SessionSummary] }>()
const sessionIdQuery = ref('')
const sessionTitleQuery = ref('')
const questionQuery = ref('')
const source = ref('全部来源')
const status = ref('全部状态')

const filtered = computed(() => sessions.filter((item) => {
  const sessionIdMatch = item.sessionId.toLowerCase().includes(sessionIdQuery.value.toLowerCase())
  const sessionTitleMatch = item.sessionTitle.toLowerCase().includes(sessionTitleQuery.value.toLowerCase())
  const questionMatch = item.title.toLowerCase().includes(questionQuery.value.toLowerCase())
  return sessionIdMatch && sessionTitleMatch && questionMatch && (source.value === '全部来源' || item.source === source.value) && (status.value === '全部状态' || item.status === status.value)
}))
const completedTurnIds = computed(() => filtered.value.filter((item) => item.status !== '运行中').map((item) => item.turnId))
const metrics = computed(() => calculateOverviewMetrics(filtered.value, modelRequests, operationSamples))

const formatSeconds = (ms?: number) => ms == null ? '—' : `${(ms / 1000).toFixed(1)}s`
</script>

<template>
  <main class="page-wrap overview-page">
    <section class="hero-row">
      <div>
        <div class="eyebrow">EXECUTION INTELLIGENCE</div>
        <h1>今天，Codex 慢在哪里？</h1>
        <p>把行为记录与性能数据放在同一时间坐标下，先找到瓶颈，再检查证据。</p>
      </div>
      <div class="live-pill"><span class="live-dot" />本地采集正常 <span class="muted">· 12 秒前</span></div>
    </section>

    <section class="filter-bar execution-filter" aria-label="执行轮次筛选">
      <el-input v-model="sessionIdQuery" :prefix-icon="Search" placeholder="会话 ID" clearable />
      <el-input v-model="sessionTitleQuery" :prefix-icon="Search" placeholder="会话标题" clearable />
      <el-input v-model="questionQuery" :prefix-icon="Search" placeholder="用户问题关键词" clearable />
      <el-select v-model="source" aria-label="客户端来源"><el-option v-for="item in ['全部来源', 'Desktop', 'CLI', 'IDE']" :key="item" :label="item" :value="item" /></el-select>
      <el-select v-model="status" aria-label="状态"><el-option v-for="item in ['全部状态', '成功', '失败', '运行中']" :key="item" :label="item" :value="item" /></el-select>
      <el-select model-value="最近 24 小时" aria-label="时间范围"><el-option label="最近 24 小时" value="最近 24 小时" /></el-select>
      <el-button :icon="Filter">筛选说明</el-button>
    </section>

    <section class="metric-grid" aria-label="核心指标">
      <article class="metric-card accent-blue"><div class="metric-head"><span>执行轮次数 <MetricHelp title="执行轮次数" unit="开始时间落入筛选范围的执行轮次" formula="COUNT(turn_id)" :denominator="`当前共 ${metrics.turns} 个轮次`" exclusions="无" source="JSONL" /></span></div><strong>{{ metrics.turns }}</strong><small>来自 {{ metrics.sessions }} 个会话</small></article>
      <article class="metric-card"><div class="metric-head"><span>P95 总耗时 <MetricHelp title="P95 总耗时" unit="已经结束的执行轮次" formula="轮次结束时间 − 用户问题时间，取第 95 百分位" :denominator="`${metrics.durationSamples} 个具有有效耗时的已结束轮次`" :exclusions="`${metrics.running} 个运行中或 ${metrics.completed - metrics.durationSamples} 个无效耗时轮次`" source="JSONL；存在 OTel 时用于校验" /></span></div><strong>{{ formatSeconds(metrics.p95DurationMs) }}</strong><small>有效样本 {{ metrics.durationSamples }} / {{ metrics.turns }}</small></article>
      <article class="metric-card"><div class="metric-head"><span>P95 请求 TTFT <MetricHelp title="P95 模型请求 TTFT" unit="所有成功获得首个可见文本增量的模型请求" formula="首个可见文本增量时间 − 请求开始时间，取第 95 百分位" :denominator="`${metrics.requestSamples} 个有效模型请求；一个轮次可贡献多个样本`" :exclusions="`${metrics.requestTotal - metrics.requestSamples} 个失败、取消、无文本增量或无效耗时请求`" source="OTel 请求与流事件" /></span></div><strong>{{ formatSeconds(metrics.p95TtftMs) }}</strong><small>有效请求 {{ metrics.requestSamples }} / {{ metrics.requestTotal }}</small></article>
      <article class="metric-card"><div class="metric-head"><span>P95 工具调用耗时 <MetricHelp title="P95 工具调用耗时" unit="筛选范围内所有已完成的工具调用" formula="工具调用结束时间 − 开始时间，取第 95 百分位" :denominator="`${metrics.toolSamples} 个具有有效耗时的工具调用`" :exclusions="`${metrics.toolTotal - metrics.toolSamples} 个无效或缺少耗时的调用`" source="OTel Trace；缺失时使用标记过的 JSONL 估算" /></span></div><strong>{{ formatSeconds(metrics.p95ToolMs) }}</strong><small>有效调用 {{ metrics.toolSamples }} / {{ metrics.toolTotal }}</small></article>
      <article class="metric-card"><div class="metric-head"><span>失败率 <MetricHelp title="执行轮次失败率" unit="筛选范围内所有已结束轮次" formula="失败轮次数 ÷ 已结束轮次数 × 100%" :denominator="`${metrics.completed} 个已结束轮次`" :exclusions="`${metrics.running} 个运行中轮次`" source="JSONL 轮次最终状态" /></span></div><strong>{{ metrics.failureRate.toFixed(1) }}<span>%</span></strong><small>{{ metrics.failed }} / {{ metrics.completed }} 个已结束轮次</small></article>
    </section>

    <section class="overview-grid overview-grid-single">
      <article class="panel trend-panel">
        <div class="panel-heading"><div><h2>耗时趋势</h2><p>最近 6 小时 · 秒</p></div><el-segmented :model-value="'P95'" :options="['P50', 'P95']" size="small" /></div>
        <TrendChart />
      </article>
    </section>

    <AnomalyContributionPanel :turn-ids="completedTurnIds" />

    <section class="panel sessions-panel">
      <div class="panel-heading"><div><h2>需要关注的执行轮次</h2><p>按总耗时降序 · {{ filtered.length }} 条结果</p></div><button class="text-button">查看全部 <el-icon><ArrowRight /></el-icon></button></div>
      <div class="session-table" role="table" aria-label="慢执行轮次列表">
        <div class="session-row session-header" role="row"><span>用户问题</span><span>所属会话</span><span>来源</span><span>总耗时</span><span>请求 TTFT</span><span>主要瓶颈</span><span>数据</span><span /></div>
        <button v-for="item in filtered" :key="item.id" class="session-row" role="row" @click="emit('open', item)">
          <span class="task-cell"><b>{{ item.title }}</b><small>{{ item.turnId }} · {{ item.startedAt }}</small></span>
          <span class="session-identity"><b>{{ item.sessionTitle }}</b><small class="mono">{{ item.sessionId }}</small></span>
          <span><el-tag size="small" effect="plain">{{ item.source }}</el-tag></span>
          <strong>{{ formatSeconds(item.durationMs) }}</strong>
          <span>{{ formatSeconds(item.ttftMs) }}</span>
          <span class="bottleneck-cell"><b>{{ item.bottleneck }}</b><small>{{ item.diagnosis }}</small></span>
          <span><el-tag size="small" :type="item.completeness === '完整' ? 'success' : item.completeness === '仅 JSONL' || item.completeness === '仅 OTel' ? 'warning' : 'info'">{{ item.completeness }}</el-tag></span>
          <el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
        <el-empty v-if="!filtered.length" description="没有匹配的会话" :image-size="72" />
      </div>
    </section>
  </main>
</template>
