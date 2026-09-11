<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, CopyDocument, DataLine, FolderOpened, InfoFilled, Refresh, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { AlignmentLevel } from '../types'

const scanning = ref(false)
const copied = ref(false)
const alignmentFilter = ref<'ALL' | AlignmentLevel>('ALL')
const currentPage = ref(1)
const pageSize = 5

function rescan() {
  scanning.value = true
  window.setTimeout(() => { scanning.value = false; ElMessage.success('模拟补扫完成：从已保存偏移继续读取，未发现遗漏事件') }, 900)
}

async function copyConfig() {
  const text = `[otel]\nenvironment = "local"\nexporter = { otlp-http = { endpoint = "http://127.0.0.1:4318" } }`
  try { await navigator.clipboard.writeText(text); copied.value = true; ElMessage.success('配置示例已复制') } catch { ElMessage.info('请手动复制配置示例') }
}

const receivers = [
  { name: 'OTLP Logs', endpoint: '/v1/logs', status: '就绪', last: '8 秒前', count: '1,284', avg: '8ms', p99: '31ms', max: '46ms', failed: 0 },
  { name: 'OTLP Traces', endpoint: '/v1/traces', status: '就绪', last: '12 秒前', count: '896', avg: '14ms', p99: '58ms', max: '73ms', failed: 1 },
  { name: 'OTLP Metrics', endpoint: '/v1/metrics', status: '就绪', last: '31 秒前', count: '42 批', avg: '6ms', p99: '19ms', max: '24ms', failed: 0 },
]

const levelMeta: Record<AlignmentLevel, { label: string; type: 'success' | 'primary' | 'warning' | 'info' }> = {
  EXACT: { label: '精确', type: 'success' }, BOUNDED: { label: '区间', type: 'primary' }, INFERRED: { label: '推测', type: 'warning' }, UNMATCHED: { label: '未关联', type: 'info' },
}

const associations = [
  { id: 'rel-demo-001', time: '14:32:17', turnId: 'turn-demo-001', jsonl: '运行支付回调测试', otel: 'shell · npm test', level: 'EXACT' as const, evidence: 'call_id 完全一致', delta: '200ms' },
  { id: 'rel-demo-002', time: '14:32:12', turnId: 'turn-demo-001', jsonl: 'rg 搜索回调处理器', otel: 'shell · rg', level: 'EXACT' as const, evidence: 'call_id 完全一致', delta: '150ms' },
  { id: 'rel-demo-003', time: '14:32:01', turnId: 'turn-demo-001', jsonl: '分析代码与制定步骤', otel: 'responses.create', level: 'BOUNDED' as const, evidence: '同会话、同轮次', delta: '600ms' },
  { id: 'rel-demo-004', time: '14:32:59', turnId: 'turn-demo-001', jsonl: '归纳测试输出', otel: 'responses.create', level: 'INFERRED' as const, evidence: '类型相符且时间重叠', delta: '200ms' },
  { id: 'rel-demo-005', time: '14:33:12', turnId: 'turn-demo-001', jsonl: '最终输出', otel: '—', level: 'UNMATCHED' as const, evidence: '没有可靠公共 ID', delta: '—' },
  { id: 'rel-demo-006', time: '13:48:29', turnId: 'turn-demo-002', jsonl: '模型可见输出', otel: 'response.output_text.delta', level: 'BOUNDED' as const, evidence: '同一轮次时间边界', delta: '340ms' },
  { id: 'rel-demo-007', time: '12:17:21', turnId: 'turn-demo-003', jsonl: '运行前端测试', otel: 'shell', level: 'INFERRED' as const, evidence: '两个同名并行候选', delta: '90ms' },
  { id: 'rel-demo-008', time: '12:17:22', turnId: 'turn-demo-003', jsonl: '运行后端测试', otel: 'shell', level: 'UNMATCHED' as const, evidence: '无法消除并行歧义', delta: '—' },
  { id: 'rel-demo-009', time: '11:26:18', turnId: 'turn-demo-004', jsonl: '安装依赖', otel: 'shell · npm install', level: 'EXACT' as const, evidence: 'call_id 完全一致', delta: '110ms' },
  { id: 'rel-demo-010', time: '10:04:09', turnId: 'turn-demo-005', jsonl: '等待迁移审批', otel: '—', level: 'UNMATCHED' as const, evidence: '该轮次只有 JSONL', delta: '—' },
  { id: 'rel-demo-011', time: '09:58:42', turnId: 'turn-demo-009', jsonl: '读取 schema', otel: 'read_file', level: 'EXACT' as const, evidence: '公共事件 ID', delta: '75ms' },
  { id: 'rel-demo-012', time: '09:41:03', turnId: 'turn-demo-010', jsonl: '生成变更摘要', otel: 'responses.create', level: 'INFERRED' as const, evidence: '候选窗口内唯一请求', delta: '480ms' },
]

const filteredAssociations = computed(() => alignmentFilter.value === 'ALL' ? associations : associations.filter((item) => item.level === alignmentFilter.value))
const pagedAssociations = computed(() => filteredAssociations.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
function changeFilter(value: 'ALL' | AlignmentLevel) { alignmentFilter.value = value; currentPage.value = 1 }
</script>

<template>
  <main class="page-wrap collection-page">
    <section class="hero-row compact-hero">
      <div><div class="eyebrow">INGESTION HEALTH</div><h1>采集状态</h1><p>检查 JSONL、OTLP 和本地存储是否健康，以及数据是否能够可靠关联。</p></div>
      <el-popover placement="bottom-end" width="390" trigger="hover">
        <template #reference><div class="live-pill health-trigger"><span class="live-dot" />全部组件正常 <el-icon><InfoFilled /></el-icon></div></template>
        <div class="health-definition"><b>“采集正常”的判定标准</b><ul><li>扫描器最近 30 秒内有成功心跳，且连续错误为 0</li><li>OTLP 三个接口监听成功，最近 5 分钟失败率低于 1%</li><li>接收队列使用率低于 80%，没有持续积压</li><li>SQLite 可写，最近一次事务成功且待写队列低于阈值</li></ul><p>任一关键条件失败显示“异常”；接近阈值显示“告警”。</p></div>
      </el-popover>
    </section>

    <section class="collector-summary">
      <article class="panel summary-status"><span class="status-icon good"><Check /></span><div><small>运行状态</small><b>采集正常</b><em>4 / 4 项健康检查通过</em></div></article>
      <article class="panel summary-status"><span class="status-icon"><DataLine /></span><div><small>今日接收</small><b>2,180</b><em>事件与 Span</em></div></article>
      <article class="panel summary-status"><span class="status-icon warn"><Warning /></span><div><small>未关联</small><b>63</b><em>影响诊断证据覆盖</em></div></article>
      <article class="panel summary-status"><span class="status-icon"><FolderOpened /></span><div><small>本地存储</small><b>18.6 MB</b><em>SQLite · 可写</em></div></article>
    </section>

    <section class="collection-grid">
      <div class="collection-main">
        <article class="panel collector-panel">
          <div class="panel-heading"><div><h2>JSONL 扫描器</h2><p>读取 Agent 行为时间线</p></div><el-tag type="success" effect="light">正在监听</el-tag></div>
          <div class="collector-path"><el-icon><FolderOpened /></el-icon><code>/workspace/demo-codex/sessions</code><span>演示目录</span></div>
          <div class="collector-stats"><div><span>已发现文件</span><b>26</b></div><div><span>读取进度</span><b>100%</b></div><div><span>最后心跳</span><b>12 秒前</b></div><div><span>连续错误</span><b>0</b></div></div>
          <div class="scan-progress"><span>读取偏移已安全保存</span><el-progress :percentage="100" status="success" /><el-button :icon="Refresh" :loading="scanning" @click="rescan">手动增量补扫</el-button></div>
          <div class="rescan-explainer"><el-icon><InfoFilled /></el-icon><span><b>作用：</b>从每个文件上次保存的字节偏移继续读取，用于补回服务重启、文件监听丢事件或短暂解析失败期间遗漏的新行。操作会按事件 ID 去重，不会重导历史数据，也不能补回缺失的 OTel。</span></div>
        </article>

        <article class="panel collector-panel receiver-panel">
          <div class="panel-heading"><div><h2>OTLP/HTTP 接收器</h2><p>处理耗时 = 收到请求 → 解码、校验并成功加入写入队列</p></div><el-tag type="success" effect="light">监听中 · 127.0.0.1:4318</el-tag></div>
          <div class="receiver-table wide">
            <div class="receiver-row receiver-header"><span>信号 / 接口</span><span>状态</span><span>最近接收</span><span>今日累计</span><span>平均</span><span>P99</span><span>最大</span><span>失败</span></div>
            <div v-for="item in receivers" :key="item.name" class="receiver-row"><span><b>{{ item.name }}</b><code>{{ item.endpoint }}</code></span><span class="receiver-ok"><i />{{ item.status }}</span><span>{{ item.last }}</span><strong>{{ item.count }}</strong><span>{{ item.avg }}</span><span>{{ item.p99 }}</span><span>{{ item.max }}</span><strong :class="{ 'failed-count': item.failed > 0 }">{{ item.failed }}</strong></div>
          </div>
          <p class="receiver-footnote">失败次数统计接收后返回非 2xx 的请求，包括解码失败、校验失败或无法进入写入队列；不代表 Codex 模型请求失败。</p>
        </article>

        <article class="panel collector-panel correlation-health">
          <div class="panel-heading"><div><h2>关联情况</h2><p>数据解释覆盖：未关联不代表执行失败或性能异常</p></div><strong>92% 有候选关系</strong></div>
          <div class="correlation-bar"><i class="exact" style="width: 61%" /><i class="bounded" style="width: 20%" /><i class="inferred" style="width: 11%" /><i class="unmatched" style="width: 8%" /></div>
          <div class="correlation-counts"><button :class="{ active: alignmentFilter === 'ALL' }" @click="changeFilter('ALL')">全部 790</button><button v-for="(meta, level) in levelMeta" :key="level" :class="{ active: alignmentFilter === level }" @click="changeFilter(level)"><i :class="level.toLowerCase()" />{{ meta.label }} {{ level === 'EXACT' ? 482 : level === 'BOUNDED' ? 158 : level === 'INFERRED' ? 87 : 63 }}</button></div>
          <div class="association-table">
            <div class="association-row association-header"><span>时间 / 轮次</span><span>JSONL 事件</span><span>OTel 对象</span><span>状态</span><span>证据 / 时间差</span></div>
            <div v-for="item in pagedAssociations" :key="item.id" class="association-row"><span><b>{{ item.time }}</b><small>{{ item.turnId }}</small></span><span>{{ item.jsonl }}</span><span>{{ item.otel }}</span><span><el-tag :type="levelMeta[item.level].type" size="small">{{ levelMeta[item.level].label }}</el-tag></span><span><b>{{ item.evidence }}</b><small>{{ item.delta }}</small></span></div>
          </div>
          <el-pagination v-model:current-page="currentPage" small background layout="prev, pager, next, total" :page-size="pageSize" :total="filteredAssociations.length" />
        </article>
      </div>

      <aside class="collection-aside">
        <article class="panel health-check-panel"><div class="panel-heading"><div><h2>健康检查</h2><p>最近一次判定 · 4 秒前</p></div></div><ul><li><span class="live-dot" /><b>JSONL 扫描心跳</b><em>12s &lt; 30s</em></li><li><span class="live-dot" /><b>OTLP 接收失败率</b><em>0.04% &lt; 1%</em></li><li><span class="live-dot" /><b>接收队列使用率</b><em>18% &lt; 80%</em></li><li><span class="live-dot" /><b>SQLite 写入</b><em>最近事务成功</em></li></ul></article>
        <article class="panel config-panel">
          <div class="panel-heading"><div><h2>Codex OTel 配置</h2><p>检测结果与配置建议</p></div><el-tag type="warning" effect="plain">演示</el-tag></div>
          <div class="config-check"><span class="status-icon good"><Check /></span><div><b>已检测到 OTel 配置</b><small>endpoint 指向本机接收器</small></div></div>
          <pre>[otel]
environment = "local"
exporter = { otlp-http = {
  endpoint = "http://127.0.0.1:4318"
} }</pre>
          <el-button class="copy-button" :icon="copied ? Check : CopyDocument" @click="copyConfig">{{ copied ? '已复制' : '复制配置示例' }}</el-button>
          <p class="config-note">Trace Lens 只生成建议，不会自动修改 <code>~/.codex/config.toml</code>。</p>
        </article>
        <article class="panel privacy-panel"><h2>数据边界</h2><ul><li><span class="live-dot" />服务仅监听回环地址</li><li><span class="live-dot" />数据仅保存在本机</li><li><span class="live-dot" />未配置任何外部上传</li></ul><small>本页面全部为 Mock 状态，不代表当前机器的真实配置。</small></article>
      </aside>
    </section>
  </main>
</template>
