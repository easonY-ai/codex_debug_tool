<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, ArrowRight, Check, Connection, DataAnalysis, Document, InfoFilled, Monitor, Refresh, Search, Tickets, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { extraNodes, traceNodes, transcriptOnly, turns, type Node, type Source, type Turn } from './data'
import { anomalyContribution, conservedTimeParts, percentile } from './analysis'
import { transcriptDocuments, transcriptJsonl } from './transcriptFixtures'
import TrendChart from './TrendChart.vue'

type Page = 'overview' | 'trace' | 'live' | 'collection'
const page = ref<Page>('overview')
const current = ref<Turn>(turns[1])
const selected = ref<Node | null>(null)
const inspectedTurnId = ref(turns[1].id)
const drawer = ref(false)
const inspectorTab = ref('evidence')
const querySession = ref('')
const queryTitle = ref('')
const queryQuestion = ref('')
const filterClient = ref('全部来源')
const filterState = ref('全部状态')
const sourceVisible = ref<Source[]>(['OTel 执行', 'Transcript 内容', 'OTel 性能'])
const zoom = ref(1)
const liveTurn = ref<Turn>(turns[2])
const noTraffic = ref(false)
const transcriptReady = ref(false)
const collectionTab = ref('receivers')
const copied = ref(false)
const selectedLevel = ref('全部')
const unknownDialog = ref(false)
const mappingPath = ref('$.payload.message')
const mappingSaved = ref(false)

const filtered = computed(() => turns.filter(turn =>
  turn.sessionId.toLowerCase().includes(querySession.value.toLowerCase()) &&
  turn.sessionTitle.toLowerCase().includes(queryTitle.value.toLowerCase()) &&
  turn.question.toLowerCase().includes(queryQuestion.value.toLowerCase()) &&
  (filterClient.value === '全部来源' || turn.client === filterClient.value) &&
  (filterState.value === '全部状态' || turn.state === filterState.value)
))
const completed = computed(() => filtered.value.filter(turn => turn.state !== '运行中' && turn.state !== '不完整'))
const withTtft = computed(() => filtered.value.filter(turn => turn.ttft != null))
const durationP95 = computed(() => percentile(completed.value.map(turn => turn.duration), .95))
const ttftP50 = computed(() => percentile(withTtft.value.map(turn => turn.ttft as number), .5))
const toolP95 = computed(() => percentile(filtered.value.filter(turn => turn.id === turns[0].id || turn.id === turns[1].id).map(turn => turn.id === turns[0].id ? 42 : 21), .95))
const failureRate = computed(() => completed.value.length ? Math.round(completed.value.filter(turn => turn.state === '失败').length / completed.value.length * 100) : null)
const trendValues = computed(() => [...turns].reverse().map(turn => filtered.value.includes(turn) ? turn.duration : null))
const operationSamples = [
  { turnId: 'turn-demo-101', model: 2, tool: 12 },
  { turnId: 'turn-demo-102', model: 2, tool: 4 },
  { turnId: 'turn-demo-103', model: 20, tool: 4 },
  { turnId: 'turn-demo-104', model: 2, tool: 4 },
  { turnId: 'turn-demo-105', model: 2, tool: 4 },
  { turnId: 'turn-demo-106', model: 2, tool: 4 }
]
const anomaly = computed(() => anomalyContribution(operationSamples, filtered.value.map(turn => turn.id)))
const nodes = computed(() => extraNodes[current.value.id] ?? (current.value.id === turns[1].id ? traceNodes : [
  { id: 'start', title: '用户提交', source: 'OTel 执行', lane: '用户 / 轮次', start: 0, end: 0.6, state: '已收到', content: current.value.question, raw: { event: 'codex.user_prompt', 'conversation.id': current.value.sessionId, 'turn.id': current.value.id } },
  { id: 'request', title: '模型请求', source: 'OTel 性能', lane: 'API / TTFT', start: 1, end: Math.max(2, current.value.duration - 2), state: current.value.state, content: current.value.ttft == null ? 'TTFT 未知' : `TTFT ${current.value.ttft} 秒`, raw: { event: 'codex.api_request', 'turn.id': current.value.id } },
  { id: 'state', title: current.value.state === '运行中' ? '仍在执行' : `轮次${current.value.state}`, source: 'OTel 执行', lane: '用户 / 轮次', start: Math.max(2, current.value.duration - 1), end: current.value.duration, state: current.value.state, content: current.value.content === 'PENDING' ? 'Transcript 内容等待补齐' : current.value.diagnosis, raw: { 'turn.id': current.value.id, state: current.value.state } }
] satisfies Node[]))
const visibleNodes = computed(() => nodes.value.filter(node => sourceVisible.value.includes(node.source)))
const inspectedDocument = computed(() => transcriptDocuments[inspectedTurnId.value])
const linkedTranscriptRecords = computed(() => {
  if (!selected.value?.transcriptRecordIds || !inspectedDocument.value) return []
  return inspectedDocument.value.records
    .map((record, index) => ({ ...record, line: index + 1 }))
    .filter(record => selected.value?.transcriptRecordIds?.includes(record.id))
})
const parentOtelRecord = computed(() => {
  if (!selected.value?.parent) return null
  return nodes.value.find(node => node.id === selected.value?.parent)?.raw ?? null
})
const duration = computed(() => current.value.duration)
const coverage = computed(() => {
  const values: Record<string, [number, number, number, number]> = {
    'turn-demo-101': [100, 100, 84, 100],
    'turn-demo-102': [100, 100, 68, 100],
    'turn-demo-103': [100, 92, 0, 80],
    'turn-demo-104': [100, 100, 0, 72],
    'turn-demo-105': [100, 96, 0, 100],
    'turn-demo-106': [100, 56, 0, 80]
  }
  return values[current.value.id]
})
const breakdown = computed(() => {
  if (current.value.id === turns[1].id) {
    return [{ label: '模型请求', value: 15.4, color: 'model' }, { label: '工具执行', value: 21, color: 'tool' }, { label: '本地处理', value: 1.6, color: 'local' }, { label: '未归因', value: 4, color: 'unknown' }]
  }
  const [model, tool, local, unknown] = conservedTimeParts(duration.value, duration.value * .35, duration.value * .45, duration.value * .05)
  return [{ label: '模型请求', value: model, color: 'model' }, { label: '工具执行', value: tool, color: 'tool' }, { label: '本地处理', value: local, color: 'local' }, { label: '未归因', value: unknown, color: 'unknown' }]
})
const levelRows = computed(() => [
  { level: 'EXACT', target: '模型 Tool Call · shell', source: 'OTel call_id ↔ Transcript call_id', evidence: '0.154.0 本地工具模型 Call ID 同值' },
  { level: 'BOUNDED', target: '子执行 · 后端测试', source: 'Transcript CommandExecution', evidence: '同一模型 Tool Call 下的子执行' },
  { level: 'INFERRED', target: '同类工具候选', source: 'OTel Span ↔ Transcript Item', evidence: '时间和类型相近，公共身份未验证' },
  { level: 'UNMATCHED', target: '并行工具候选', source: '2 个可能目标', evidence: '多个候选，未选择最近记录' }
].filter(row => selectedLevel.value === '全部' || row.level === selectedLevel.value))
const mapValid = computed(() => /^\$(?:\.[A-Za-z_][\w-]*|\['[^']+'\]|\[\d+\]|\[\*\])+$/.test(mappingPath.value))

function navigate(target: Page) { page.value = target; window.scrollTo({ top: 0, behavior: 'smooth' }) }
function openTurn(turn: Turn) { current.value = turn; selected.value = null; zoom.value = 1; navigate('trace') }
function inspect(node: Node) {
  inspectedTurnId.value = page.value === 'live' ? liveTurn.value.id : current.value.id
  selected.value = node
  inspectorTab.value = 'evidence'
  drawer.value = true
}
function width(node: Node) { return `${Math.max(2.2, ((node.end - node.start) / duration.value) * 100)}%` }
function left(node: Node) { return `${(node.start / duration.value) * 100}%` }
function toggleSource(source: Source) { sourceVisible.value = sourceVisible.value.includes(source) ? sourceVisible.value.filter(item => item !== source) : [...sourceVisible.value, source] }
function copyConfig() { navigator.clipboard?.writeText('核对有效的用户或 CLI OTel 配置；检查 logs、traces、metrics exporter 与本机 OTLP 接收地址；不要把项目级 otel 配置视为已启用。'); copied.value = true; window.setTimeout(() => copied.value = false, 1800) }
</script>

<template>
  <div class="app">
    <header class="topbar">
      <button class="brand" @click="navigate('overview')"><span class="brand-mark"><i /><i /><i /></span><span><b>TRACE LENS</b><small>Codex 执行分析</small></span></button>
      <nav aria-label="主导航">
        <button :class="{ active: page === 'overview' || page === 'trace' }" @click="navigate('overview')"><el-icon><DataAnalysis /></el-icon>分析总览</button>
        <button :class="{ active: page === 'live' }" @click="navigate('live')"><el-icon><Tickets /></el-icon>实时会话</button>
        <button :class="{ active: page === 'collection' }" @click="navigate('collection')"><el-icon><Monitor /></el-icon>采集状态</button>
      </nav>
      <span class="prototype-label">V3 · MOCK</span>
    </header>

    <main v-if="page === 'overview'" class="page">
      <div class="page-title"><div><span class="eyebrow">EXECUTION OVERVIEW</span><h1>分析总览</h1><p>按执行轮次查看行为、性能与内容证据</p></div><span class="health-pill"><i />采集部分可用 · 12 秒前</span></div>
      <section class="filters" aria-label="筛选执行轮次">
        <el-input v-model="querySession" placeholder="会话 ID" clearable :prefix-icon="Search" aria-label="会话 ID" />
        <el-input v-model="queryTitle" placeholder="会话标题" clearable :prefix-icon="Search" aria-label="会话标题" />
        <el-input v-model="queryQuestion" placeholder="用户问题" clearable :prefix-icon="Search" aria-label="用户问题" />
        <el-select v-model="filterClient" aria-label="客户端来源"><el-option v-for="x in ['全部来源', 'Desktop', 'CLI', 'IDE']" :key="x" :label="x" :value="x" /></el-select>
        <el-select v-model="filterState" aria-label="执行状态"><el-option v-for="x in ['全部状态', '成功', '失败', '中断', '运行中', '不完整']" :key="x" :label="x" :value="x" /></el-select>
      </section>
      <section class="metric-strip" aria-label="筛选结果指标">
        <div><span>执行轮次 <el-tooltip content="对象：当前筛选范围内有 OTel Session/Turn 骨架的轮次；Transcript-only 不进入分母。"><el-icon><InfoFilled /></el-icon></el-tooltip></span><strong>{{ filtered.length }}</strong><small>OTel Turn</small></div>
        <div><span>总耗时 P95 <el-tooltip content="对象：已终止且有有效 OTel 起止时间的轮次；排除运行中及缺失终态。"><el-icon><InfoFilled /></el-icon></el-tooltip></span><strong>{{ durationP95 == null ? '—' : durationP95.toFixed(1) }}<em v-if="durationP95 != null">s</em></strong><small>有效样本 {{ completed.length }}</small></div>
        <div><span>模型请求 TTFT <el-tooltip content="对象：成功取得首个可见文本增量的 OTel 模型请求；每轮可贡献多个样本。排除失败和无首增量请求。"><el-icon><InfoFilled /></el-icon></el-tooltip></span><strong>{{ ttftP50 == null ? '—' : ttftP50.toFixed(1) }}<em v-if="ttftP50 != null">s</em></strong><small>请求级 P50 · 有效样本 {{ withTtft.length }}</small></div>
        <div><span>工具执行 P95 <el-tooltip content="对象：有有效 OTel 工具区间的模型 Tool Call；并行子执行不相加。"><el-icon><InfoFilled /></el-icon></el-tooltip></span><strong>{{ toolP95 == null ? '—' : toolP95.toFixed(1) }}<em v-if="toolP95 != null">s</em></strong><small>父调用区间并集</small></div>
        <div><span>失败率 <el-tooltip content="分子：失败轮次；分母：当前筛选的已终止 OTel Turn。中断留在分母，运行中与缺失终态排除。"><el-icon><InfoFilled /></el-icon></el-tooltip></span><strong>{{ failureRate == null ? '—' : failureRate }}<em v-if="failureRate != null">%</em></strong><small>有效样本 {{ completed.length }}</small></div>
      </section>
      <section class="section">
        <div class="section-head"><div><h2>异常耗时贡献</h2><p>当前范围同类 P50 基线 · 至少 5 个样本 · 超出 1 秒且相对增幅达 20%</p></div><el-tag type="info" effect="plain">OTel 聚合</el-tag></div>
        <div v-for="row in anomaly" :key="row.key" class="contribution"><div><span class="swatch" :class="row.key" />{{ row.label }}</div><strong>{{ row.count < 5 ? '样本不足' : `${row.share}%` }}</strong><div class="bar"><i :class="row.key" :style="{ width: `${row.share}%` }" /></div><small>{{ row.count < 5 ? `有效样本 ${row.count} / 5` : `异常 ${row.excess.toFixed(1)}s · P50 ${row.baseline.toFixed(1)}s · ${row.count} 个样本` }}</small></div>
      </section>
      <section class="section trend-section"><div class="section-head"><div><h2>轮次耗时趋势</h2><p>当前筛选范围 · OTel Turn 总耗时</p></div><el-tag type="info" effect="plain">最近 6 个轮次</el-tag></div><TrendChart :values="trendValues" /></section>
      <section class="section turn-section">
        <div class="section-head"><div><h2>执行轮次</h2><p>{{ filtered.length }} 条结果 · 仅有 OTel 执行骨架的轮次</p></div><button class="link-button" @click="navigate('collection')">查看来源状态 <el-icon><ArrowRight /></el-icon></button></div>
        <div class="table-scroll"><div class="turn-row table-head"><span>用户问题 / 会话</span><span>来源</span><span>状态</span><span>总耗时</span><span>TTFT</span><span>诊断 / 内容</span><span>完整度</span><span></span></div>
          <button v-for="turn in filtered" :key="turn.id" class="turn-row" @click="openTurn(turn)"><span class="question"><b>{{ turn.question }}</b><small>{{ turn.sessionTitle }} · {{ turn.sessionId }} / {{ turn.id }}</small></span><span>{{ turn.client }}</span><span><span class="state" :class="turn.state">{{ turn.state }}</span></span><strong>{{ turn.duration.toFixed(1) }}s</strong><span>{{ turn.ttft == null ? '未知' : `${turn.ttft.toFixed(1)}s` }}</span><span class="diagnosis">{{ turn.diagnosis }}<small>{{ turn.content }}</small></span><span>{{ turn.completeness }}%</span><el-icon><ArrowRight /></el-icon></button>
          <div v-if="!filtered.length" class="empty">没有符合条件的执行轮次</div>
        </div>
      </section>
    </main>

    <main v-else-if="page === 'trace'" class="page trace-page">
      <button class="back" @click="navigate('overview')"><el-icon><ArrowLeft /></el-icon>分析总览</button>
      <div class="page-title trace-title"><div><span class="eyebrow">TURN TRACE</span><h1>{{ current.question }}</h1><p>{{ current.sessionTitle }} · {{ current.sessionId }} / {{ current.id }} · {{ current.client }}</p></div><div class="title-facts"><span class="state" :class="current.state">{{ current.state }}</span><b>{{ current.duration.toFixed(1) }}s</b></div></div>
      <section class="trace-facts"><div><span>OTel 执行骨架</span><b>已建立</b></div><div><span>Transcript 内容</span><b :class="{ warning: current.content !== 'AVAILABLE' }">{{ current.content }}</b></div><div><span>模型请求 TTFT</span><b>{{ current.ttft == null ? '未知' : `${current.ttft.toFixed(1)}s` }}</b></div><div><span>数据完整度 <el-popover placement="bottom" trigger="hover" :width="260"><template #reference><el-icon><InfoFilled /></el-icon></template><div class="completeness-popover"><b>四项等权</b><div><span>OTel Session/Turn</span><strong>{{ coverage[0] }}%</strong></div><div><span>OTel 工具/请求事件</span><strong>{{ coverage[1] }}%</strong></div><div><span>Transcript 内容</span><strong>{{ coverage[2] }}%</strong></div><div><span>OTel 性能 Span</span><strong>{{ coverage[3] }}%</strong></div><small>关联可信度单独展示</small></div></el-popover></span><b>{{ current.completeness }}%</b></div></section>
      <div class="trace-grid"><div class="trace-primary">
        <section class="section timeline-section"><div class="section-head"><div><h2>共享执行时间轴</h2><p>OTel 执行 · Transcript 内容 · OTel 性能</p></div><div class="zoom-controls"><button title="缩小" aria-label="缩小" @click="zoom = Math.max(1, zoom - .25)"><el-icon><ZoomOut /></el-icon></button><span>{{ Math.round(zoom * 100) }}%</span><button title="放大" aria-label="放大" @click="zoom = Math.min(2.5, zoom + .25)"><el-icon><ZoomIn /></el-icon></button></div></div>
          <div class="source-switches"><button v-for="source in ['OTel 执行','Transcript 内容','OTel 性能'] as Source[]" :key="source" :class="['source-switch', sourceVisible.includes(source) ? 'on' : '', source === 'OTel 执行' ? 'execution' : source === 'Transcript 内容' ? 'transcript' : 'performance']" @click="toggleSource(source)"><span class="swatch" />{{ source }}</button></div>
          <div class="timeline-scroll"><div class="timeline" :style="{ width: `${zoom * 100}%` }"><div class="ruler"><span v-for="i in 5" :key="i">{{ Math.round((i - 1) * duration / 4) }}s</span></div><div v-for="lane in ['用户 / 轮次','API / TTFT','决策','工具调用','前端子执行','后端子执行','可见内容']" :key="lane" class="lane"><span class="lane-label">{{ lane }}</span><div class="lane-track"><button v-for="node in visibleNodes.filter(n => n.lane === lane)" :key="node.id" :class="['trace-node', node.source === 'OTel 执行' ? 'execution' : node.source === 'Transcript 内容' ? 'transcript' : 'performance', node.state === '失败' ? 'failed' : '']" :style="{ left: left(node), width: width(node) }" :title="node.title" @click="inspect(node)">{{ node.end - node.start < 2 ? '◆' : node.title }}</button></div></div></div></div>
          <div class="turn-total"><div class="total-head"><b>本轮耗时摘要</b><span>总计 {{ current.duration.toFixed(1) }}s · 并行区间不重复累计</span></div><div class="total-bar"><i v-for="part in breakdown" :key="part.label" :class="part.color" :style="{ width: `${part.value / duration * 100}%` }" /></div><div class="total-labels"><span v-for="part in breakdown" :key="part.label"><i :class="part.color" />{{ part.label }} {{ part.value.toFixed(1) }}s</span><span v-if="current.id === turns[1].id">审批等待：未知</span></div></div>
        </section>
        <section class="section"><div class="section-head"><div><h2>可访问的时间轴节点</h2><p>选择节点查看来源原文与关联证据</p></div></div><div class="node-list"><button v-for="node in visibleNodes" :key="node.id" @click="inspect(node)"><span class="node-source" :class="node.source === 'OTel 执行' ? 'execution' : node.source === 'Transcript 内容' ? 'transcript' : 'performance'">{{ node.source }}</span><b>{{ node.title }}</b><small>+{{ node.start.toFixed(1) }}s</small><span>{{ node.state }}</span><el-icon><ArrowRight /></el-icon></button></div></section>
      </div><aside class="trace-aside"><section class="section"><h2>本轮诊断</h2><div class="finding"><span>01</span><div><b>{{ current.diagnosis }}</b><p>只基于有证据的 OTel 区间与 Transcript 命令结果。</p></div></div><div v-if="current.id === turns[1].id" class="finding"><span>02</span><div><b>审批等待耗时未知</b><p>仅观察到决策事件；未覆盖区间已计入未归因。</p></div></div></section><section class="section"><h2>工具层级</h2><div v-if="current.id === turns[1].id" class="tool-tree"><div><b>Model Tool Call</b><span>call-demo-7 · 1 次 · OTel 21.0s</span></div><div class="child"><b>CommandExecution · 前端</b><span>成功 · exit 0 · Transcript</span></div><div class="child"><b>CommandExecution · 后端</b><span>失败 · exit 7 · Transcript</span></div></div><div v-else-if="current.id === turns[0].id" class="tool-tree"><div><b>Model Tool Call</b><span>call-demo-payment · 1 次 · OTel 42.0s</span></div></div><p v-else class="subtle">当前没有可确认的工具调用层级。</p></section><section class="section"><h2>关联等级</h2><p class="subtle">当前已验证版本的本地模型 Call ID 可精确关联。其他版本需重新验证。</p><div class="level-list"><span><i class="exact" />EXACT · 公共身份</span><span><i class="bounded" />BOUNDED · 同轮边界</span><span><i class="inferred" />INFERRED · 类型与时间</span><span><i class="unmatched" />UNMATCHED · 无法消歧</span></div></section></aside></div>
    </main>

    <main v-else-if="page === 'live'" class="page"><div class="page-title"><div><span class="eyebrow">LIVE EXECUTION</span><h1>实时会话</h1><p>当前执行轮次与最近完成记录</p></div><span class="health-pill"><i />OTel 事件流已连接</span></div><div class="live-grid"><section class="section live-list"><div class="section-head"><h2>轮次</h2><span class="subtle">{{ turns.length }} 条</span></div><button v-for="turn in turns.slice(0,4)" :key="turn.id" :class="['live-row', liveTurn.id === turn.id ? 'active' : '']" @click="liveTurn = turn"><span class="state-dot" :class="turn.state" /><span><b>{{ turn.sessionTitle }}</b><small>{{ turn.question }}</small><small>{{ turn.sessionId }} / {{ turn.id }}</small></span><em>{{ turn.duration }}s</em></button></section><div><section class="section live-detail"><div class="section-head"><div><h2>{{ liveTurn.sessionTitle }}</h2><p>{{ liveTurn.sessionId }} / {{ liveTurn.id }}</p></div><span class="state" :class="liveTurn.state">{{ liveTurn.state }}</span></div><p class="live-question">{{ liveTurn.question }}</p><div class="live-stages"><span class="done">用户入口</span><span class="done">模型请求</span><span :class="liveTurn.state === '运行中' ? 'active' : 'done'">{{ liveTurn.state === '运行中' ? '执行中' : '工具 / 输出' }}</span><span :class="liveTurn.state === '运行中' ? '' : 'done'">{{ liveTurn.state === '运行中' ? '等待终态' : '已结束' }}</span></div><div class="live-summary"><div><span>运行时长</span><b>{{ liveTurn.duration }}s</b></div><div><span>内容</span><b>{{ liveTurn.content }}</b></div><div><span>TTFT</span><b>{{ liveTurn.ttft == null ? '未知' : `${liveTurn.ttft}s` }}</b></div></div><button class="primary-button" @click="openTurn(liveTurn)">打开 Trace <el-icon><ArrowRight /></el-icon></button></section><section class="section stream"><div class="section-head"><h2>最新事件</h2><span class="subtle">OTel 执行与性能</span></div><button v-for="node in (extraNodes[liveTurn.id] ?? [{ id:'live-entry', title:'用户提交', source:'OTel 执行' as Source, lane:'用户 / 轮次', start:0, end:.5, state:'已收到', raw:{event:'codex.user_prompt','turn.id':liveTurn.id} }, { id:'live-api', title:'模型请求', source:'OTel 性能' as Source, lane:'API / TTFT', start:1, end:liveTurn.duration, state:'运行中', raw:{event:'codex.api_request','turn.id':liveTurn.id} }])" :key="node.id" class="stream-row" @click="inspect(node)"><time>+{{ node.start.toFixed(1) }}s</time><span class="swatch" :class="node.source === 'OTel 执行' ? 'execution' : 'performance'" /><b>{{ node.title }}</b><small>{{ node.source }}</small><el-icon><ArrowRight /></el-icon></button><div v-if="liveTurn.content === 'PENDING'" class="stream-pending">Transcript 内容等待补齐 · 最终回复尚未生成</div></section></div></div></main>

    <main v-else class="page"><div class="page-title"><div><span class="eyebrow">COLLECTION HEALTH</span><h1>采集状态</h1><p>OTel 接收与 Transcript 来源分别检查</p></div><span class="health-pill warning"><i />数据覆盖需检查</span></div><div class="status-grid"><section class="status-block"><span>有效 OTel 配置</span><b>已检测 · 用户级</b><small>配置建议只复制，不自动修改文件</small></section><section class="status-block"><span>接收器</span><b>LISTENING</b><small>127.0.0.1 · logs / traces / metrics</small></section><section class="status-block"><span>已到达请求投递</span><b>{{ noTraffic ? 'IDLE' : 'READY' }}</b><small>{{ noTraffic ? '0 请求 · 成功率未知' : '72 / 72 · 100%' }}</small></section><section class="status-block"><span>业务数据覆盖</span><b class="warning">{{ noTraffic ? 'UNKNOWN' : 'PARTIAL' }}</b><small>接收成功不等于覆盖完整</small></section></div>
      <div class="collection-layout"><div><section class="section"><div class="section-head"><h2>来源状态</h2><label class="switch-label"><el-switch v-model="noTraffic" />零请求场景</label></div><el-tabs v-model="collectionTab"><el-tab-pane label="OTLP 接收" name="receivers"><div class="receiver-scroll"><div class="receiver-row receiver-head"><span>信号</span><span>监听</span><span>请求</span><span>投递</span><span>成功率</span><span>P99</span><span>覆盖</span></div><div v-for="signal in ['logs','traces','metrics']" :key="signal" class="receiver-row"><b>{{ signal }}<small>/v1/{{ signal }}</small></b><span>LISTENING</span><span>{{ noTraffic ? 0 : signal === 'logs' ? 42 : signal === 'traces' ? 24 : 6 }}</span><span>{{ noTraffic ? 'IDLE' : 'READY' }}</span><span>{{ noTraffic ? '未知' : '100%' }}</span><span>{{ noTraffic ? '—' : '18ms' }}</span><span>{{ noTraffic ? 'UNKNOWN' : 'PARTIAL' }}</span></div></div></el-tab-pane><el-tab-pane label="Transcript" name="transcripts"><div class="source-line"><b>已发现文件</b><span>31</span><small>安全根目录内，增量读取</small></div><div class="source-line"><b>session_meta 匹配</b><span>27 / 31</span><small>不匹配记录保留但不挂接</small></div><div class="source-line"><b>内容待补齐</b><span>{{ transcriptReady ? '0' : '2' }}</span><small>后到时按同一 Turn 重算</small></div><button class="secondary-button" @click="transcriptReady = true"><el-icon><Refresh /></el-icon>{{ transcriptReady ? '增量重试完成 · 0 条重复' : '重试增量读取' }}</button></el-tab-pane><el-tab-pane label="关联明细" name="alignments"><div class="level-filters"><button v-for="x in ['全部','EXACT','BOUNDED','INFERRED','UNMATCHED']" :key="x" :class="{ active: selectedLevel === x }" @click="selectedLevel = x">{{ x }}</button></div><div v-for="row in levelRows" :key="row.level" class="association-row"><span class="level" :class="row.level.toLowerCase()">{{ row.level }}</span><b>{{ row.target }}</b><span>{{ row.source }}</span><small>{{ row.evidence }}</small></div></el-tab-pane></el-tabs></section><section class="section"><div class="section-head"><div><h2>Transcript 原始检查</h2><p>没有 OTel 骨架时不创建正式 Trace</p></div><el-tag type="warning" effect="plain">INSPECTION_ONLY</el-tag></div><div class="inspection-row"><el-icon><Document /></el-icon><div><b>{{ transcriptOnly.sessionId }}</b><small>{{ transcriptOnly.path }}</small></div><el-popover trigger="click" placement="bottom" :width="360"><template #reference><button class="link-button">查看原始记录 <el-icon><ArrowRight /></el-icon></button></template><pre class="raw">{{ JSON.stringify(transcriptOnly.raw, null, 2) }}</pre></el-popover></div></section></div><aside><section class="section"><h2>配置检查</h2><p class="subtle">OTel 默认关闭；项目级配置中的 otel 项不会启用 exporter。检查有效用户或 CLI 配置。</p><div class="config-sample">用户/CLI 有效配置 · exporter · logs / traces / metrics · 本机接收地址</div><button class="secondary-button" @click="copyConfig"><el-icon><Check v-if="copied" /><Document v-else /></el-icon>{{ copied ? '已复制' : '复制核查清单' }}</button></section><section class="section"><h2>来源健康</h2><div class="health-row"><span>Transcript 路径</span><b>VALID 27 / 31</b></div><div class="health-row"><span>Meta 身份冲突</span><b class="warning">1</b></div><div class="health-row"><span>MySQL 写入</span><b>最近事务成功</b></div><div class="health-row"><span>接收队列</span><b>3 / 1,000</b></div></section><section class="section"><h2>未知记录</h2><div class="health-row"><span>结构指纹</span><b>1 种 · 3 条</b></div><button class="secondary-button" @click="unknownDialog = true">检查映射 <el-icon><ArrowRight /></el-icon></button></section></aside></div>
    </main>

    <el-drawer v-model="drawer" size="min(640px, 100vw)" class="inspector">
      <template #header>
        <div class="inspector-heading">
          <span>{{ selected?.source }}</span>
          <h2>{{ selected?.title }}</h2>
        </div>
      </template>
      <template v-if="selected">
        <div class="inspector-meta">
          <span>{{ selected.state }}</span>
          <span>+{{ selected.start.toFixed(1) }}s 至 +{{ selected.end.toFixed(1) }}s</span>
        </div>
        <el-tabs v-model="inspectorTab">
          <el-tab-pane label="证据" name="evidence">
            <div class="inspector-section">
              <h3>内容与状态</h3>
              <p>{{ selected.content ?? '该节点没有可见正文' }}</p>
              <div v-if="selected.parent" class="callout">属于父模型 Tool Call；子执行状态由 Transcript 拥有。</div>
              <div v-if="selected.id === 'decision'" class="callout">仅有批准决策证据，审批等待耗时未知。</div>
            </div>
            <div class="inspector-section">
              <h3>跨源关联</h3>
              <span v-if="selected.level" class="level" :class="selected.level.toLowerCase()">{{ selected.level }}</span>
              <p>{{ selected.evidence ?? '当前没有可确认的跨源关系。' }}</p>
            </div>
          </el-tab-pane>
          <el-tab-pane label="原始记录" name="raw">
            <div class="inspector-section">
              <h3>{{ selected.source === 'Transcript 内容' ? 'Transcript JSONL 原始记录' : `${selected.source} 原始证据` }}</h3>
              <pre class="raw">{{ JSON.stringify(selected.raw, null, 2) }}</pre>
            </div>
            <div v-if="linkedTranscriptRecords.length" class="inspector-section">
              <h3>关联的 Transcript JSONL 原始记录 · {{ linkedTranscriptRecords.length }} 条</h3>
              <div v-for="record in linkedTranscriptRecords" :key="record.id" class="linked-record">
                <span>第 {{ record.line }} 行 · {{ record.id }}</span>
                <pre class="raw">{{ JSON.stringify(record.raw, null, 2) }}</pre>
              </div>
            </div>
            <div v-else-if="parentOtelRecord" class="inspector-section">
              <h3>关联的父模型 Tool Call · OTel 原始证据</h3>
              <pre class="raw">{{ JSON.stringify(parentOtelRecord, null, 2) }}</pre>
            </div>
            <div v-else-if="selected.source !== 'Transcript 内容'" class="callout">该节点没有可确认的 Transcript 原始记录。</div>
          </el-tab-pane>
          <el-tab-pane v-if="inspectedDocument" label="完整 Transcript" name="transcript">
            <div class="inspector-section">
              <h3>完整 Transcript · {{ inspectedDocument.records.length }} 条 JSONL</h3>
              <p class="transcript-path">{{ inspectedDocument.path }}</p>
              <pre class="raw full-transcript">{{ transcriptJsonl(inspectedDocument) }}</pre>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
    <el-dialog v-model="unknownDialog" title="UNKNOWN 结构映射" width="min(560px, 94vw)"><p class="subtle">结构指纹：<code>demo-fingerprint-01</code> · 3 条虚构记录</p><el-form label-position="top"><el-form-item label="正文 JSONPath"><el-input v-model="mappingPath" /></el-form-item></el-form><div class="mapping-preview"><b>预览</b><span v-if="mapValid">1 个字符串匹配：合成消息内容</span><span v-else class="warning">仅支持根 $、成员、数组下标和 [*]</span></div><template #footer><el-button @click="unknownDialog = false">取消</el-button><el-button type="primary" :disabled="!mapValid" @click="mappingSaved = true; unknownDialog = false">{{ mappingSaved ? '已保存当前指纹' : '保存并重处理当前指纹' }}</el-button></template></el-dialog>
  </div>
</template>
