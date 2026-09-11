<script setup lang="ts">
import { ref } from 'vue'
import { DataAnalysis, Monitor, Setting, Tickets } from '@element-plus/icons-vue'
import OverviewPage from './components/OverviewPage.vue'
import TracePage from './components/TracePage.vue'
import LiveSessionsPage from './components/LiveSessionsPage.vue'
import CollectionStatusPage from './components/CollectionStatusPage.vue'
import type { SessionSummary } from './types'

type Page = 'overview' | 'trace' | 'live' | 'collection'
const page = ref<Page>('overview')
const activeSession = ref<SessionSummary | null>(null)

function go(target: Page) {
  page.value = target
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openSession(session: SessionSummary) {
  activeSession.value = session
  go('trace')
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <button class="brand" @click="go('overview')"><span class="brand-mark"><i /><i /><i /></span><span><b>TRACE LENS</b><small>Codex 执行分析</small></span></button>
      <nav aria-label="主导航">
        <button :class="{ active: page === 'overview' || page === 'trace' }" @click="go('overview')"><el-icon><DataAnalysis /></el-icon>分析总览</button>
        <button :class="{ active: page === 'live' }" @click="go('live')"><el-icon><Tickets /></el-icon>实时会话</button>
        <button :class="{ active: page === 'collection' }" @click="go('collection')"><el-icon><Monitor /></el-icon>采集状态</button>
      </nav>
      <div class="top-actions"><span class="prototype-badge">MOCK PROTOTYPE</span><button class="icon-button" aria-label="设置"><el-icon><Setting /></el-icon></button><span class="avatar">TL</span></div>
    </header>
    <OverviewPage v-if="page === 'overview'" @open="openSession" />
    <TracePage v-else-if="page === 'trace'" :key="activeSession?.id" @back="go('overview')" />
    <LiveSessionsPage v-else-if="page === 'live'" />
    <CollectionStatusPage v-else />
  </div>
</template>
