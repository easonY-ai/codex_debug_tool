<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue'

defineProps<{
  title: string
  unit: string
  formula: string
  denominator: string
  exclusions: string
  source: string
}>()
</script>

<template>
  <el-tooltip placement="top" effect="light" :show-after="180">
    <template #content>
      <div class="metric-help-card">
        <b>{{ title }}</b>
        <dl><dt>统计对象</dt><dd>{{ unit }}</dd><dt>计算方式</dt><dd>{{ formula }}</dd><dt>样本分母</dt><dd>{{ denominator }}</dd><dt>排除项</dt><dd>{{ exclusions }}</dd><dt>数据来源</dt><dd>{{ source }}</dd></dl>
      </div>
    </template>
    <button class="metric-help-trigger" :aria-label="`查看${title}的统计口径`"><el-icon><QuestionFilled /></el-icon></button>
  </el-tooltip>
</template>
