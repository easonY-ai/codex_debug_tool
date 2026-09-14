<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { Alignment, TimelineItem } from '../types'

const props = defineProps<{
  modelValue: boolean
  item?: TimelineItem
  counterpart?: TimelineItem
  alignment?: Alignment
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const jsonl = computed(() => props.item?.layer === 'Hook 行为' ? props.item : props.counterpart?.layer === 'Hook 行为' ? props.counterpart : undefined)
const otel = computed(() => props.item?.layer === 'OTel 性能' ? props.item : props.counterpart?.layer === 'OTel 性能' ? props.counterpart : undefined)
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
          <div class="drawer-source-note"><b>Hook 行为节点 · {{ jsonl.contentStatus }}</b><span>{{ jsonl.timingPrecision }} · +{{ seconds(jsonl.startMs) }}</span></div>
          <div class="io-view drawer-io">
            <section class="io-block input-block"><header><span>INPUT</span><b>JSONL 补齐的输入</b></header><pre v-if="jsonl.input">{{ jsonl.input }}</pre><div v-else class="io-empty">Hook 节点已存在，输入尚未从 JSONL 补齐。</div></section>
            <section class="io-block output-block"><header><span>OUTPUT</span><b>JSONL 补齐的输出</b></header><pre v-if="jsonl.output">{{ jsonl.output }}</pre><div v-else class="io-empty">内容尚未生成或尚未从 JSONL 补齐。</div></section>
          </div>
        </el-tab-pane>
        <el-tab-pane v-if="!jsonl" label="性能详情" name="性能详情">
          <div class="not-jsonl"><el-icon><InfoFilled /></el-icon><b>这是 OTel 性能节点</b><p>{{ item.description }}</p><p>OTel 用于解释耗时，不承载完整输入与输出。</p></div>
        </el-tab-pane>
        <el-tab-pane label="原始数据" name="原始数据">
          <div v-if="jsonl" class="raw-source-card"><header><span>Hook / 推导证据</span><small>执行骨架</small></header><pre class="raw-view">{{ JSON.stringify(jsonl.hookRaw, null, 2) }}</pre></div>
          <div v-if="jsonl && Object.keys(jsonl.raw).length" class="raw-source-card jsonl-source"><header><span>JSONL 原文</span><small>内容补齐</small></header><pre class="raw-view">{{ JSON.stringify(jsonl.raw, null, 2) }}</pre></div>
          <div v-else-if="jsonl" class="io-empty">当前 Hook 节点尚无 JSONL 内容记录。</div>
          <div v-if="otel" class="raw-source-card otel-source"><header><span>OTel 原文</span><small>{{ otel.title }}</small></header><pre class="raw-view">{{ JSON.stringify(otel.raw, null, 2) }}</pre></div>
        </el-tab-pane>
        <el-tab-pane label="关联说明" name="关联说明">
          <div v-if="alignment" class="drawer-alignment"><el-tag :type="alignment.level === 'EXACT' ? 'success' : alignment.level === 'INFERRED' ? 'warning' : 'info'">{{ levelLabel }}关联</el-tag><b>{{ alignment.evidence }}</b><span v-if="alignment.timeDeltaMs != null">两侧起始时间差 {{ alignment.timeDeltaMs }}ms</span><p v-if="alignment.level === 'INFERRED'">这是算法候选关系，不代表两个节点必然是同一次调用。</p></div>
          <el-empty v-else description="当前节点没有跨源关联" :image-size="68" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>
