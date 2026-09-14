<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import type { ECharts } from 'echarts/core'
import { init } from 'echarts/core'
import { trendData } from '../mockData'

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent])

const props = defineProps<{ compact?: boolean }>()
const root = ref<HTMLDivElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

function render() {
  chart?.setOption({
    animationDuration: 650,
    color: ['#5b8cff', '#f5a742', '#37b38c'],
    tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => `${value} 秒` },
    legend: { top: 2, right: 4, itemWidth: 16, textStyle: { color: '#687083', fontSize: 12 } },
    grid: { top: 42, right: 16, bottom: 24, left: 38, containLabel: false },
    xAxis: { type: 'category', boundaryGap: false, data: trendData.map((item) => item.time), axisLine: { lineStyle: { color: '#dce2ed' } }, axisTick: { show: false }, axisLabel: { color: '#8790a2' } },
    yAxis: { type: 'value', axisLabel: { color: '#8790a2', formatter: '{value}s' }, splitLine: { lineStyle: { color: '#edf0f5' } } },
    series: [
      { name: '总耗时', type: 'line', smooth: 0.35, symbolSize: 7, data: trendData.map((item) => item.duration), lineStyle: { width: 3 }, areaStyle: { color: 'rgba(91,140,255,.09)' } },
      { name: 'TTFT', type: 'line', smooth: 0.35, symbolSize: 6, data: trendData.map((item) => item.ttft), lineStyle: { width: 2 } },
      { name: '工具耗时', type: 'line', smooth: 0.35, symbolSize: 6, data: trendData.map((item) => item.tool), lineStyle: { width: 2 } },
    ],
  })
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(root.value)
})

watch(() => props.compact, () => chart?.resize())
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <div ref="root" class="trend-chart" role="img" aria-label="最近六小时总耗时、首 Token 与工具耗时趋势图" />
</template>
