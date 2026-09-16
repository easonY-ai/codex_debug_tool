<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { Alignment, TimelineItem } from '../types'
import { transcriptMappingDescription } from '../data/traceMapper'

const props = defineProps<{
  modelValue: boolean
  item?: TimelineItem
  counterpart?: TimelineItem
  alignment?: Alignment
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const jsonl = computed(() => props.item?.layer === 'Agent 行为' ? props.item : props.counterpart?.layer === 'Agent 行为' ? props.counterpart : undefined)
const otel = computed(() => props.item?.layer === 'OTel 性能' ? props.item : props.counterpart?.layer === 'OTel 性能' ? props.counterpart : undefined)
const transcriptEvidence = computed(() => jsonl.value?.jsonlEvidence ?? [])
const activeTab = ref('输入 / 输出')
watch([() => props.item, () => props.modelValue], () => { activeTab.value = jsonl.value ? '输入 / 输出' : '性能详情' })
const levelLabel = computed(() => ({ EXACT: '精确', BOUNDED: '区间', INFERRED: '推测', UNMATCHED: '未关联' }[props.alignment?.level ?? 'UNMATCHED']))
const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}s`
</script>

<template>
  <el-drawer :model-value="modelValue" size="520px" direction="rtl" class="event-inspector-drawer" @update:model-value="emit('update:modelValue', $event)">
    <template #header>
      <div v-if="item" class="drawer-event-title"><span :class="['node-status', item.status]" /><div><small>{{ item.layer }} · {{ item.source }}</small><h2>{{ item.title }}</h2></div><strong>{{ seconds(item.durationMs) }}</strong></div>
    </template>
    <template v-if="item">
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane v-if="jsonl" label="输入 / 输出" name="输入 / 输出">
          <div class="drawer-source-note"><b>Hook 行为节点 / JSONL 内容补齐</b><span>时间轴 +{{ seconds(jsonl.startMs) }}</span></div>
          <div class="io-view drawer-io">
            <section class="io-block input-block"><header><span>INPUT</span><b>该步骤收到的输入</b></header><pre v-if="jsonl.input">{{ jsonl.input }}</pre><div v-else class="io-empty">该事件没有输入字段。</div></section>
            <section class="io-block output-block"><header><span>OUTPUT</span><b>该步骤产生的输出</b></header><pre v-if="jsonl.output">{{ jsonl.output }}</pre><div v-else class="io-empty">该事件没有输出。用户问题是执行入口，只包含输入。</div></section>
          </div>
        </el-tab-pane>
        <el-tab-pane v-if="!jsonl" label="性能详情" name="性能详情">
          <div class="not-jsonl"><el-icon><InfoFilled /></el-icon><b>这是 OTel 性能节点</b><p>{{ item.description }}</p><p>OTel 用于解释耗时，不承载完整输入与输出。</p></div>
        </el-tab-pane>
        <el-tab-pane label="原始数据" name="原始数据">
          <div v-if="jsonl" class="raw-source-card"><header><span>Hook 原始证据</span><small>{{ jsonl.title }}</small></header><pre class="raw-view">{{ JSON.stringify(jsonl.raw, null, 2) }}</pre></div>
          <div v-for="evidence in jsonl?.jsonlEvidence" :key="evidence.id" class="raw-source-card jsonl-source"><header><span>JSONL 原文</span><small>{{ evidence.contentKind }} · {{ evidence.mappingLevel }} · {{ evidence.adapterVersion }}</small></header><pre class="raw-view">{{ JSON.stringify(evidence.raw, null, 2) }}</pre></div>
          <div v-if="jsonl && !jsonl.jsonlEvidence?.length" class="raw-missing">该 Hook 节点的 JSONL 内容尚未补齐。</div>
          <div v-if="otel" class="raw-source-card otel-source"><header><span>OTel 原文</span><small>{{ otel.title }}</small></header><pre class="raw-view">{{ JSON.stringify(otel.raw, null, 2) }}</pre></div>
        </el-tab-pane>
        <el-tab-pane label="关联说明" name="关联说明">
          <div v-for="evidence in transcriptEvidence" :key="`transcript-${evidence.id}`" class="drawer-alignment">
            <el-tag :type="evidence.mappingLevel === 'EXACT' ? 'success' : 'primary'">{{ evidence.mappingLevel === 'EXACT' ? '精确内容关联' : '区间内容关联' }}</el-tag>
            <b>{{ transcriptMappingDescription(evidence) }}</b>
            <span>适配器 {{ evidence.adapterVersion }}</span>
            <p v-if="evidence.mappingLevel === 'EXACT'"><code>Hook tool_use_id={{ evidence.hookNodeId }}</code><br><code>JSONL call_id={{ evidence.callId }}</code></p>
          </div>
          <div v-if="alignment" class="drawer-alignment"><el-tag :type="alignment.level === 'EXACT' ? 'success' : alignment.level === 'INFERRED' ? 'warning' : 'info'">{{ levelLabel }}关联</el-tag><b>{{ alignment.evidence }}</b><span v-if="alignment.timeDeltaMs != null">两侧起始时间差 {{ alignment.timeDeltaMs }}ms</span><p v-if="alignment.level === 'INFERRED'">这是算法候选关系，不代表两个节点必然是同一次调用。</p></div>
          <el-empty v-if="!alignment && !transcriptEvidence.length" description="当前节点没有跨源关联" :image-size="68" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>
