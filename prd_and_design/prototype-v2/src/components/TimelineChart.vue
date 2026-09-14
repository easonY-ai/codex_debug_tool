<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CustomChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { DataZoomComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { init, type ECharts } from 'echarts/core'
import type { TimelineItem } from '../types'

use([CanvasRenderer, CustomChart, DataZoomComponent, GridComponent, TooltipComponent])

const props = defineProps<{ items: TimelineItem[]; selectedId?: string }>()
const emit = defineEmits<{ select: [item: TimelineItem] }>()
const root = ref<HTMLDivElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

const lanes = ['用户', '模型', '工具', 'API / 传输', '推理', '审批 / 等待', '工具 Span']
const maxTime = computed(() => Math.max(...props.items.map((item) => item.startMs + item.durationMs), 1))

const colors: Record<TimelineItem['status'], string> = {
  success: '#4f84ff', warning: '#f2a33a', danger: '#e55b62', info: '#8b96aa',
}

function renderItem(params: any, api: any) {
  const item = props.items[params.dataIndex]
  const laneIndex = api.value(0)
  const start = api.coord([api.value(1), laneIndex])
  const end = api.coord([api.value(2), laneIndex])
  const bandHeight = Math.min(api.size([0, 1])[1] * 0.56, 31)
  const width = Math.max(end[0] - start[0], 4)
  const selected = item.id === props.selectedId
  const shape = { x: start[0], y: start[1] - bandHeight / 2, width, height: bandHeight, r: 6 }
  const clipped = (window as any).echarts?.graphic?.clipRectByRect?.(shape, params.coordSys) ?? shape
  if (!clipped) return null
  const children: any[] = [{
    type: 'rect', shape: clipped,
    style: { fill: colors[item.status], opacity: item.layer === 'Hook 行为' ? 0.92 : 0.72, stroke: selected ? '#16213a' : '#ffffff', lineWidth: selected ? 3 : 1 },
  }]
  if (width > 68) children.push({
    type: 'text',
    style: { x: start[0] + 9, y: start[1], text: item.title, fill: '#fff', font: '500 12px sans-serif', verticalAlign: 'middle', width: width - 16, overflow: 'truncate' },
  })
  return { type: 'group', children }
}

function render() {
  if (!chart) return
  chart.setOption({
    animationDurationUpdate: 260,
    grid: { top: 24, right: 30, bottom: 58, left: 116 },
    tooltip: {
      formatter: (params: any) => {
        const item = props.items[params.dataIndex]
        return `<div class="chart-tip"><b>${item.title}</b><br/>${item.layer} · ${item.source}<br/>${(item.startMs / 1000).toFixed(1)}s → ${((item.startMs + item.durationMs) / 1000).toFixed(1)}s<br/><b>${(item.durationMs / 1000).toFixed(1)} 秒</b></div>`
      },
    },
    xAxis: { min: 0, max: Math.ceil(maxTime.value / 5000) * 5000, axisLabel: { color: '#7d879a', formatter: (value: number) => `${Math.round(value / 1000)}s` }, axisLine: { lineStyle: { color: '#dce2ed' } }, splitLine: { show: true, lineStyle: { color: '#eef1f5' } } },
    yAxis: { type: 'category', inverse: true, data: lanes, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#40485a', fontSize: 12, margin: 16 }, splitArea: { show: true, areaStyle: { color: ['rgba(248,250,253,.76)', '#fff'] } } },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, filterMode: 'weakFilter', minSpan: 18 },
      { type: 'slider', xAxisIndex: 0, height: 18, bottom: 12, borderColor: 'transparent', backgroundColor: '#edf1f7', fillerColor: 'rgba(79,132,255,.2)', handleStyle: { color: '#4f84ff' }, textStyle: { color: '#7d879a' } },
    ],
    series: [{
      type: 'custom', renderItem, encode: { x: [1, 2], y: 0 },
      data: props.items.map((item) => [lanes.indexOf(item.lane), item.startMs, item.startMs + item.durationMs]),
    }],
  }, true)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  ;(window as any).echarts = { graphic: { clipRectByRect: (shape: any, rect: any) => {
    const x = Math.max(shape.x, rect.x); const y = Math.max(shape.y, rect.y)
    const x2 = Math.min(shape.x + shape.width, rect.x + rect.width); const y2 = Math.min(shape.y + shape.height, rect.y + rect.height)
    return x2 >= x && y2 >= y ? { ...shape, x, y, width: x2 - x, height: y2 - y } : null
  } } }
  render()
  chart.on('click', (params: any) => emit('select', props.items[params.dataIndex]))
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(root.value)
})

watch(() => [props.items, props.selectedId], render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <div ref="root" class="timeline-chart" role="img" aria-label="Hook 行为骨架和 OTel 性能双层共享时间轴，可滚轮缩放和拖动平移" />
</template>
