<script setup lang="ts">
import { computed } from 'vue'
import { operationSamples } from '../mockData'
import type { AnomalyCategory, OperationSample } from '../types'
import MetricHelp from './MetricHelp.vue'

const props = defineProps<{ turnIds: string[] }>()
const categories: AnomalyCategory[] = ['模型请求', '工具执行', '审批等待', '本地处理']
const colors: Record<AnomalyCategory, string> = { 模型请求: '#5b8cff', 工具执行: '#37b38c', 审批等待: '#f5a742', 本地处理: '#98a2b5' }

function percentile(values: number[], p: number) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const index = (sorted.length - 1) * p
  const lower = Math.floor(index)
  const upper = Math.ceil(index)
  return sorted[lower] + ((sorted[upper] ?? sorted[lower]) - sorted[lower]) * (index - lower)
}

const stats = computed(() => {
  const selected = operationSamples.filter((sample) => props.turnIds.includes(sample.turnId))
  const groups = new Map<string, OperationSample[]>()
  selected.forEach((sample) => groups.set(sample.operationType, [...(groups.get(sample.operationType) ?? []), sample]))
  const rows = categories.map((category) => {
    const categorySamples = selected.filter((sample) => sample.category === category)
    let anomalyMs = 0
    const affectedTurns = new Set<string>()
    let eligibleSamples = 0
    groups.forEach((samples) => {
      if (samples[0]?.category !== category || samples.length < 5) return
      eligibleSamples += samples.length
      const baseline = percentile(samples.map((sample) => sample.durationMs), .5)
      samples.forEach((sample) => {
        const excess = sample.durationMs - baseline
        if (excess >= 1000 && sample.durationMs >= baseline * 1.2) {
          anomalyMs += excess
          affectedTurns.add(sample.turnId)
        }
      })
    })
    const durations = categorySamples.map((sample) => sample.durationMs)
    return { category, sampleCount: categorySamples.length, eligibleSamples, anomalyMs, affectedTurns: affectedTurns.size, p50: percentile(durations, .5), p95: percentile(durations, .95), p99: percentile(durations, .99), max: Math.max(0, ...durations) }
  })
  const total = rows.reduce((sum, row) => sum + row.anomalyMs, 0)
  return rows.map((row) => ({ ...row, share: total ? row.anomalyMs / total * 100 : 0 }))
})

const hasAnomaly = computed(() => stats.value.some((row) => row.anomalyMs > 0))
const seconds = (ms: number) => `${(ms / 1000).toFixed(1)}s`
</script>

<template>
  <article class="panel anomaly-panel">
    <div class="panel-heading">
      <div><h2>异常耗时贡献 <MetricHelp title="异常耗时贡献" unit="当前筛选范围内、同类样本不少于 5 个的操作" formula="单次异常耗时 = 实际耗时 − 同类操作 P50；类别贡献率 = 类别异常耗时 ÷ 全部异常耗时" denominator="所有同时超过 P50 1 秒且超过 P50 20% 的异常耗时" exclusions="运行中轮次、样本不足的操作类型和正常波动" source="OTel 优先；缺失时使用标记过的 JSONL 估算" /></h2><p>跨执行轮次统计 · 当前范围 P50 基线</p></div>
      <el-tag type="warning" effect="plain">≥1s 且 ≥20%</el-tag>
    </div>
    <template v-if="hasAnomaly">
      <div class="anomaly-stack" aria-label="各类别异常耗时贡献率">
        <i v-for="row in stats.filter(item => item.share > 0)" :key="row.category" :style="{ width: `${row.share}%`, background: colors[row.category] }" :title="`${row.category} ${row.share.toFixed(1)}%`" />
      </div>
      <div class="anomaly-table">
        <div class="anomaly-row anomaly-header"><span>类别</span><span>异常贡献</span><span>异常耗时</span><span>影响轮次</span><span>P50</span><span>P95</span><span>P99</span><span>最大</span><span>样本</span></div>
        <div v-for="row in stats" :key="row.category" class="anomaly-row">
          <span class="anomaly-category"><i :style="{ background: colors[row.category] }" />{{ row.category }}</span>
          <strong v-if="row.eligibleSamples >= 5">{{ row.share.toFixed(1) }}%</strong><el-tag v-else type="info" size="small">样本不足</el-tag>
          <span>{{ seconds(row.anomalyMs) }}</span><span>{{ row.affectedTurns }}</span><span>{{ seconds(row.p50) }}</span><span>{{ seconds(row.p95) }}</span><span>{{ seconds(row.p99) }}</span><span>{{ seconds(row.max) }}</span><span>{{ row.sampleCount }}</span>
        </div>
      </div>
      <div class="anomaly-callout"><b>为什么模型请求排在第一？</b><span>工具测试稳定在约 4.1s，没有越过异常门槛；模型请求通常约 2.2s，但两次抖动到 18.5s 和 20.1s，产生了 34.2s 异常耗时。</span></div>
    </template>
    <el-empty v-else description="当前筛选范围没有明显异常耗时，或同类样本不足 5 个" :image-size="64" />
  </article>
</template>
