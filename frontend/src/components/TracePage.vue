<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Connection, Document, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import { useAnalyzerData } from '../data/analyzerData'
import type { Alignment, AlignmentLevel, Diagnosis, TimelineItem } from '../types'
import TimelineChart from './TimelineChart.vue'
import EventInspectorDrawer from './EventInspectorDrawer.vue'
import { buildAgentTimelineItems, transcriptMappingDescription } from '../data/traceMapper'

const { alignments, diagnoses, timelineItems, turnTimeBreakdown } = useAnalyzerData()

defineEmits<{ back: [] }>()
const props = defineProps<{ session: import('../types').SessionSummary }>()
const session = computed(() => props.session)
const selectedItem = ref<TimelineItem>()
const selectedAlignment = ref<Alignment | null>(null)
const activeTab = ref('输入 / 输出')
const inspectorOpen = ref(false)
const visibleItems = ref(timelineItems.map((item) => item.id))

const filteredItems = computed(() => timelineItems.filter((item) => visibleItems.value.includes(item.id)))
const selectedRelations = computed(() => alignments.filter((item) => item.agentEventId === selectedItem.value?.id || item.performanceSpanId === selectedItem.value?.id))
const counterpartItem = computed(() => {
  const relation = selectedRelations.value[0]
  if (!relation) return undefined
  const counterpartId = selectedItem.value?.layer === 'Agent 行为' ? relation.performanceSpanId : relation.agentEventId
  return timelineItems.find((item) => item.id === counterpartId)
})
const jsonlItem = computed(() => selectedItem.value?.layer === 'Agent 行为' ? selectedItem.value : counterpartItem.value?.layer === 'Agent 行为' ? counterpartItem.value : undefined)
const transcriptEvidence = computed(() => jsonlItem.value?.jsonlEvidence ?? [])
const otelItem = computed(() => selectedItem.value?.layer === 'OTel 性能' ? selectedItem.value : counterpartItem.value?.layer === 'OTel 性能' ? counterpartItem.value : undefined)
const parsedFields = computed(() => {
  const raw = jsonlItem.value?.raw ?? {}
  return [
    ['session_id', raw.session_id], ['turn_id', raw.turn_id], ['call_id', raw.call_id],
    ['type', raw.type], ['timestamp', raw.timestamp], ['cwd', raw.cwd], ['model', raw.model],
  ].filter(([, value]) => value != null)
})

const levelMeta: Record<AlignmentLevel, { label: string; type: 'success' | 'primary' | 'warning' | 'info' }> = {
  EXACT: { label: '精确', type: 'success' }, BOUNDED: { label: '区间', type: 'primary' }, INFERRED: { label: '推测', type: 'warning' }, UNMATCHED: { label: '未关联', type: 'info' },
}

function selectItem(item: TimelineItem) {
  selectedItem.value = item
  selectedAlignment.value = null
  activeTab.value = item.layer === 'Agent 行为' ? '输入 / 输出' : '详情'
  inspectorOpen.value = true
}

function selectAlignment(item: Alignment) {
  selectedAlignment.value = item
  const target = timelineItems.find((node) => node.id === item.performanceSpanId) ?? timelineItems.find((node) => node.id === item.agentEventId)
  if (target) selectedItem.value = target
  activeTab.value = '关联证据'
  inspectorOpen.value = true
}

function focusDiagnosis(item: Diagnosis) {
  const target = timelineItems.find((node) => node.id === item.targetId)
  if (target) selectItem(target)
}

const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}s`
const breakdownClass: Record<string, string> = { 模型请求: 'model', 工具执行: 'tool', 审批等待: 'approval', 本地处理: 'local', 未归因: 'unknown' }
onMounted(async()=>{try{const response=await fetch(`/api/sessions/${encodeURIComponent(session.value.turnId)}/analysis`);if(!response.ok)throw new Error(`HTTP ${response.status}`);const body=await response.json();const start=Number(body.session.startedAt??0)
  const agent:TimelineItem[]=buildAgentTimelineItems(body.agentEvents,body.jsonlSupplements??[],start)
  const performance:TimelineItem[]=body.performanceSpans.map((span:any)=>({id:`otel-${span.id}`,layer:'OTel 性能',lane:span.objectKind?.includes('tool')?'工具 Span':'API / 传输',title:span.objectKind||span.signalType,startMs:Math.max(0,Number(span.eventTime??start)-start),durationMs:Number(span.durationMs??0),status:'success',source:'OTel Trace',description:'OTLP 原始性能对象',traceId:span.traceId,raw:JSON.parse(span.rawJson)}))
  timelineItems.splice(0,timelineItems.length,...agent,...performance);visibleItems.value=timelineItems.map(item=>item.id);selectedItem.value=timelineItems[0]
  alignments.splice(0,alignments.length,...body.alignments.map((a:any)=>({id:String(a.id),agentEventId:`hook-${body.agentEvents.find((e:any)=>JSON.parse(e.rawJson).tool_use_id===a.hookNodeId)?.id}`,performanceSpanId:`otel-${a.otelObjectId}`,level:a.level,evidence:a.evidenceJson,timeDeltaMs:a.timeDeltaMs})))
  diagnoses.splice(0,diagnoses.length,...body.diagnoses.map((d:any)=>({id:d.id,severity:d.severity,title:d.title,detail:d.detail,impact:`${(Number(d.impactMs)/1000).toFixed(1)}s`,confidence:d.confidence})))
  turnTimeBreakdown.splice(0,turnTimeBreakdown.length,{category:'模型请求',durationMs:Number(body.aggregates.modelRequestMs),source:'OTel'},{category:'工具执行',durationMs:Number(body.aggregates.toolMs),source:body.performanceSpans.length?'OTel':'JSONL 估算'},{category:'审批等待',durationMs:Number(body.aggregates.approvalMs),source:'OTel'},{category:'本地处理',durationMs:Number(body.aggregates.localMs),source:'JSONL 估算'},{category:'未归因',durationMs:Number(body.aggregates.unattributedMs),source:'差额'})
}catch{timelineItems.splice(0);alignments.splice(0);diagnoses.splice(0)}})
</script>

<template>
  <main class="trace-page page-wrap">
    <button class="back-button" @click="$emit('back')"><el-icon><ArrowLeft /></el-icon>返回分析总览</button>

    <section class="trace-title-row">
      <div>
        <div class="title-line"><h1>{{ session.title }}</h1><el-tag type="success" effect="light">{{ session.status }}</el-tag></div>
        <p>会话 <span class="mono">{{ session.sessionId }}</span> · 轮次 <span class="mono">{{ session.turnId }}</span> · {{ session.startedAt }}</p>
      </div>
      <div class="trace-summary">
        <div><span>总耗时</span><strong>{{ seconds(session.durationMs) }}</strong></div>
        <div><span>Token</span><strong>{{ (session.tokenUsage / 1000).toFixed(1) }}k</strong></div>
        <div><span>来源</span><strong>{{ session.source }}</strong></div>
        <el-popover placement="bottom-end" width="360" trigger="hover">
          <template #reference><div class="completeness-trigger"><span>数据完整度</span><strong>{{ session.completeness }}</strong></div></template>
          <div class="completeness-help"><b>数据能力：{{ session.completeness }}</b><p>缺失的 transcript 或 OTel 不会由其他来源伪造。</p></div>
        </el-popover>
      </div>
    </section>

    <section class="trace-layout">
      <div class="trace-main">
        <article class="panel trace-chart-panel">
          <div class="panel-heading trace-heading">
            <div><h2>共享执行时间轴</h2><p>滚轮缩放 · 拖动平移 · 点击 Hook/JSONL 节点查看该位置的输入与输出</p></div>
            <div class="chart-actions"><el-checkbox-group v-model="visibleItems" class="source-toggles"><el-checkbox-button :value="timelineItems.filter(i => i.layer === 'Agent 行为').map(i => i.id)" disabled>Agent 行为</el-checkbox-button></el-checkbox-group><el-tag effect="plain">总耗时 {{ seconds(session.durationMs) }}</el-tag></div>
          </div>
          <div class="layer-marker agent"><span>HOOK</span> Agent 行为层 <small>回答“做了什么”</small></div>
          <div class="layer-marker otel"><span>OTEL</span> 性能层 <small>回答“时间花在哪里”</small></div>
          <TimelineChart :items="filteredItems" :selected-id="selectedItem?.id" @select="selectItem" />
          <div class="turn-time-summary">
            <div class="turn-summary-head"><span><b>本轮耗时摘要</b> · 缺失来源归入未归因</span><strong>{{ seconds(session.durationMs) }}</strong></div>
            <div class="turn-summary-bar"><i v-for="part in turnTimeBreakdown" :key="part.category" :class="breakdownClass[part.category]" :style="{ width: `${part.durationMs / session.durationMs * 100}%` }" /></div>
            <div class="turn-summary-items"><span v-for="part in turnTimeBreakdown" :key="part.category"><i :class="breakdownClass[part.category]" /><b>{{ part.category }}</b> {{ seconds(part.durationMs) }}<small>{{ part.source }}</small></span></div>
          </div>
          <div class="chart-legend">
            <span><i class="solid exact" />精确关联</span><span><i class="solid bounded" />区间关联</span><span><i class="dashed inferred" />推测关联</span><span><i class="dotted unmatched" />未关联</span>
            <span class="legend-hint"><el-icon><InfoFilled /></el-icon>关联不会合并原始节点</span>
          </div>
        </article>

        <article class="panel alignments-panel">
          <div class="panel-heading"><div><h2>跨源关联</h2><p>Hook/JSONL 事件与 OTel Span 之间的关系及证据</p></div><el-tag effect="plain">{{ alignments.length }} 条</el-tag></div>
          <div class="alignment-list">
            <button v-for="item in alignments" :key="item.id" :class="['alignment-row', item.level.toLowerCase(), { active: selectedAlignment?.id === item.id }]" @click="selectAlignment(item)">
              <span class="alignment-node agent-node">{{ timelineItems.find(n => n.id === item.agentEventId)?.title }}</span>
              <span class="alignment-rail"><i /><el-tag :type="levelMeta[item.level].type" size="small" effect="light">{{ levelMeta[item.level].label }}</el-tag><i /></span>
              <span class="alignment-node otel-node">{{ item.performanceSpanId ? timelineItems.find(n => n.id === item.performanceSpanId)?.title : '无匹配 Span' }}</span>
              <small>{{ item.evidence }}</small>
            </button>
          </div>
        </article>

        <article class="panel accessible-list-panel">
          <el-collapse>
            <el-collapse-item name="nodes">
              <template #title><span class="collapse-title"><el-icon><Document /></el-icon>可访问的时间轴节点列表（{{ timelineItems.length }}）</span></template>
              <button v-for="item in timelineItems" :key="item.id" class="accessible-node" @click="selectItem(item)"><span>{{ item.layer }} · {{ item.lane }}</span><b>{{ item.title }}</b><small>{{ seconds(item.startMs) }} / {{ seconds(item.durationMs) }}</small></button>
            </el-collapse-item>
          </el-collapse>
        </article>
      </div>

      <aside class="trace-aside">
        <article class="panel diagnosis-panel">
          <div class="panel-heading"><div><h2>自动诊断</h2><p>规则引擎 v1 · 不调用模型</p></div><el-tag size="small" effect="plain">确定性</el-tag></div>
          <div class="diagnosis-logic"><b>运行逻辑</b><span>先识别真正延长本轮完成时间的阶段并计算基线分位数，再依次匹配 TTFT、端到端模型请求、审批、工具、重试和未归因等待规则，最后按影响时长排序。</span></div>
          <button v-for="(item, index) in diagnoses" :key="item.id" class="diagnosis-item" @click="focusDiagnosis(item)">
            <span :class="['diag-index', `level-${index}`]">{{ index + 1 }}</span>
            <span><em>{{ item.severity }} · {{ item.confidence }}可信</em><b>{{ item.title }}</b><small>{{ item.detail }}</small><strong>{{ item.impact }}</strong></span>
          </button>
        </article>

        <article v-if="selectedItem" class="panel detail-panel">
          <div class="detail-title"><span :class="['node-status', selectedItem.status]" /><div><small>{{ selectedItem.layer }} · {{ selectedItem.source }}</small><h2>{{ selectedItem.title }}</h2></div><strong>{{ seconds(selectedItem.durationMs) }}</strong></div>
          <el-tabs v-model="activeTab" stretch class="detail-tabs">
            <el-tab-pane label="输入 / 输出" name="输入 / 输出">
              <div v-if="selectedItem.layer === 'Agent 行为'" class="io-view">
                <section class="io-block input-block"><header><span>INPUT</span><b>该节点的输入</b></header><pre v-if="selectedItem.input">{{ selectedItem.input }}</pre><div v-else class="io-empty">该 JSONL 事件没有输入，例如最终状态事件可能只记录输出。</div></section>
                <section class="io-block output-block"><header><span>OUTPUT</span><b>该节点的输出</b></header><pre v-if="selectedItem.output">{{ selectedItem.output }}</pre><div v-else class="io-empty">该 JSONL 事件没有输出。用户问题是执行入口，只包含输入。</div></section>
                <button class="raw-link" @click="activeTab = '原始数据'"><el-icon><Document /></el-icon>查看完整 JSONL 原文与解析结果</button>
              </div>
              <div v-else class="not-jsonl"><el-icon><InfoFilled /></el-icon><b>这是 OTel 性能节点</b><p>OTel 用于解释耗时，不承载完整输入与输出。可以点击时间轴上方的 JSONL 行为节点查看内容。</p><button v-if="counterpartItem" @click="selectItem(counterpartItem)">查看关联的 JSONL 节点</button></div>
            </el-tab-pane>
            <el-tab-pane label="详情" name="详情">
              <p class="node-description">{{ selectedItem.description }}</p>
              <dl class="detail-grid"><dt>开始</dt><dd>+{{ seconds(selectedItem.startMs) }}</dd><dt>结束</dt><dd>+{{ seconds(selectedItem.startMs + selectedItem.durationMs) }}</dd><dt>泳道</dt><dd>{{ selectedItem.lane }}</dd><dt>状态</dt><dd>已完成</dd></dl>
              <div v-if="selectedRelations.length || transcriptEvidence.length" class="relation-summary"><el-icon><Connection /></el-icon><span>找到 {{ selectedRelations.length + transcriptEvidence.length }} 条跨源关系</span><button @click="activeTab = '关联证据'">查看证据</button></div>
            </el-tab-pane>
            <el-tab-pane label="关联证据" name="关联证据">
              <div v-for="evidence in transcriptEvidence" :key="`transcript-${evidence.id}`" class="evidence-card">
                <div><el-tag :type="evidence.mappingLevel === 'EXACT' ? 'success' : 'primary'">{{ evidence.mappingLevel === 'EXACT' ? '精确内容关联' : '区间内容关联' }}</el-tag><span>适配器 {{ evidence.adapterVersion }}</span></div>
                <p>{{ transcriptMappingDescription(evidence) }}</p>
                <div v-if="evidence.mappingLevel === 'EXACT'" class="parsed-fields"><dl><dt>Hook tool_use_id</dt><dd>{{ evidence.hookNodeId }}</dd><dt>JSONL call_id</dt><dd>{{ evidence.callId }}</dd></dl></div>
              </div>
              <div v-if="selectedAlignment" class="evidence-card"><div><el-tag :type="levelMeta[selectedAlignment.level].type">{{ levelMeta[selectedAlignment.level].label }}关联</el-tag><span v-if="selectedAlignment.timeDeltaMs != null">时间差 {{ selectedAlignment.timeDeltaMs }}ms</span></div><p>{{ selectedAlignment.evidence }}</p><div v-if="selectedAlignment.level === 'INFERRED'" class="evidence-warning"><el-icon><WarningFilled /></el-icon>这是算法推测，不代表两个节点必然是同一调用。</div></div>
              <div v-else-if="selectedRelations.length"><button v-for="item in selectedRelations" :key="item.id" class="mini-relation" @click="selectAlignment(item)"><el-tag :type="levelMeta[item.level].type" size="small">{{ levelMeta[item.level].label }}</el-tag><span>{{ item.evidence }}</span></button></div>
              <el-empty v-if="!selectedRelations.length && !transcriptEvidence.length" description="该节点没有跨源关联" :image-size="58" />
            </el-tab-pane>
            <el-tab-pane label="原始数据" name="原始数据">
              <div v-if="jsonlItem" class="raw-source-card"><header><span>Hook 原始证据</span><small>{{ jsonlItem.title }}</small></header><pre class="raw-view">{{ JSON.stringify(jsonlItem.raw, null, 2) }}</pre></div>
              <div v-for="evidence in jsonlItem?.jsonlEvidence" :key="evidence.id" class="raw-source-card jsonl-source"><header><span>JSONL 原文</span><small>{{ evidence.contentKind }} · {{ evidence.mappingLevel }} · {{ evidence.adapterVersion }}</small></header><pre class="raw-view">{{ JSON.stringify(evidence.raw, null, 2) }}</pre></div>
              <div v-if="jsonlItem && !jsonlItem.jsonlEvidence?.length" class="raw-missing">该 Hook 节点的 JSONL 内容尚未补齐。</div>
              <div v-if="parsedFields.length" class="parsed-fields"><b>解析字段</b><dl><template v-for="field in parsedFields" :key="String(field[0])"><dt>{{ field[0] }}</dt><dd>{{ field[1] }}</dd></template></dl></div>
              <div v-if="otelItem" class="raw-source-card otel-source"><header><span>对应 OTel</span><small>{{ selectedRelations[0] ? levelMeta[selectedRelations[0].level].label + '关联' : '' }}</small></header><pre class="raw-view">{{ JSON.stringify(otelItem.raw, null, 2) }}</pre></div>
              <div v-else-if="jsonlItem" class="raw-missing">该 JSONL 事件没有找到对应的 OTel Span。</div>
              <div v-if="!jsonlItem" class="raw-source-card otel-source"><header><span>OTel 原文</span><small>{{ selectedItem.title }}</small></header><pre class="raw-view">{{ JSON.stringify(selectedItem.raw, null, 2) }}</pre></div>
            </el-tab-pane>
          </el-tabs>
        </article><article v-else class="panel detail-panel"><el-empty description="该轮次尚无可展示事件"/></article>
      </aside>
    </section>
    <EventInspectorDrawer v-model="inspectorOpen" :item="selectedItem" :counterpart="counterpartItem" :alignment="selectedRelations[0]" />
  </main>
</template>
