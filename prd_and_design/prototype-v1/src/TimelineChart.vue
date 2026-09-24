<script setup lang="ts">
import { computed } from 'vue'
import type { Node } from './data'
import { buildTimelineRows } from './timeline'

const props = defineProps<{
  nodes: Node[]
  duration: number
  zoom: number
}>()
const emit = defineEmits<{ inspect: [node: Node] }>()

const rows = computed(() => buildTimelineRows(props.nodes))
const operationCount = computed(() => rows.value.filter(row => row.operation).length)
const spanCount = computed(() => rows.value.reduce((count, row) => count + row.spans.length, 0))
const canvasWidth = computed(() => `${Math.round(990 * props.zoom)}px`)

function position(seconds: number): string {
  return `${Math.max(0, Math.min(100, seconds / props.duration * 100))}%`
}

function length(node: Node): string {
  return `${Math.max(0, (node.end - node.start) / props.duration * 100)}%`
}

function spanClass(node: Node): string {
  if (node.lane === '工具调用') return 'tool-span'
  if (node.lane === 'API / TTFT') return 'request-span'
  return 'other-span'
}
</script>

<template>
  <div class="operation-timeline-scroll" aria-label="Turn 操作与 OTel Span 二维时间轴">
    <div class="operation-timeline" :style="{ minWidth: canvasWidth }">
      <div class="operation-ruler">
        <div class="operation-ruler-label">
          <strong>操作节点</strong>
          <small>{{ operationCount }} 个节点 · {{ spanCount }} 个 Span</small>
        </div>
        <div class="operation-ruler-track">
          <span v-for="tick in 5" :key="tick">{{ ((tick - 1) * duration / 4).toFixed(0) }}s</span>
        </div>
      </div>

      <div v-for="row in rows" :key="row.id" class="operation-row">
        <button
          class="operation-label"
          :class="{ 'unknown-operation': !row.operation }"
          :aria-label="row.operation ? `查看操作 ${row.operation.title}` : `查看未知操作的 Span ${row.spans[0]?.title}`"
          @click="row.operation ? emit('inspect', row.operation) : emit('inspect', row.spans[0]!)"
        >
          <span class="operation-type">{{ row.operation?.source ?? 'OTel 性能' }}</span>
          <strong>{{ row.operation?.title ?? '（未知操作）' }}</strong>
          <small>{{ row.operation?.state ?? '未关联' }} · +{{ row.at.toFixed(1) }}s</small>
          <small v-if="!row.operation" class="unknown-span-name">{{ row.spans[0]?.title }}</small>
        </button>
        <div class="operation-track" :class="{ 'has-spans': row.spans.length }">
          <span v-if="row.operation" class="operation-marker" :style="{ left: position(row.operation.start) }" :title="`${row.operation.title} · +${row.operation.start.toFixed(1)}s`" />
          <div v-for="span in row.spans" :key="span.id" class="span-subtrack">
            <button
              class="span-interval"
              :class="[spanClass(span), { failed: span.state === '失败' }]"
              :style="{ left: position(span.start), width: length(span) }"
              :title="`${span.title} · +${span.start.toFixed(1)}s 至 +${span.end.toFixed(1)}s`"
              :aria-label="`查看 Span ${span.title}，${span.start.toFixed(1)} 秒至 ${span.end.toFixed(1)} 秒`"
              @click="emit('inspect', span)"
            >
              <span>{{ span.title }}</span>
              <small>{{ (span.end - span.start).toFixed(1) }}s</small>
            </button>
          </div>
          <div v-if="!row.spans.length" class="span-subtrack" />
        </div>
      </div>
    </div>
  </div>
</template>
