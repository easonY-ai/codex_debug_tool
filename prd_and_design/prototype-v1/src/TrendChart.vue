<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { use, init, type ECharts } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ values: (number | null)[] }>()
const root = ref<HTMLElement | null>(null)
let chart: ECharts | null = null
let observer: ResizeObserver | null = null

function render() {
  chart?.setOption({
    animation: false,
    grid: { left: 36, right: 14, top: 20, bottom: 27 },
    tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => `${value}s` },
    xAxis: { type: 'category', data: ['轮次 1', '轮次 2', '轮次 3', '轮次 4', '轮次 5', '轮次 6'], axisLine: { lineStyle: { color: '#dce3e7' } }, axisTick: { show: false }, axisLabel: { color: '#82929a', fontSize: 10 } },
    yAxis: { type: 'value', name: 's', nameTextStyle: { color: '#82929a' }, axisLabel: { color: '#82929a', fontSize: 10 }, splitLine: { lineStyle: { color: '#edf1f2' } } },
    series: [{ type: 'line', name: '轮次耗时', data: props.values, showSymbol: true, symbolSize: 6, connectNulls: false, smooth: .25, lineStyle: { color: '#238e77', width: 2 }, itemStyle: { color: '#238e77' }, areaStyle: { color: 'rgba(35,142,119,.06)' } }]
  })
}
onMounted(() => { if (!root.value) return; chart = init(root.value); render(); observer = new ResizeObserver(() => chart?.resize()); observer.observe(root.value) })
watch(() => props.values, render)
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="root" class="trend-chart" role="img" aria-label="筛选范围内执行轮次耗时趋势" /></template>
