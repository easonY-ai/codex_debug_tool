<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Check, CopyDocument, DataLine, FolderOpened, InfoFilled, Refresh, Setting, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

type UnknownStatus = 'UNMAPPED' | 'MAPPED' | 'FAILED'
type UnknownItem = { id: string; signature: string; count: number; first: string; latest: string; source: string; status: UnknownStatus; sample: Record<string, unknown> }
const scanning = ref(false), copied = ref(false), drawerOpen = ref(false)
const unknownFilter = ref<'ALL' | UnknownStatus>('ALL')
const selected = ref<UnknownItem | null>(null)
const unknownItems = reactive<UnknownItem[]>([
  { id: 'fp-a17c', signature: '$.payload.content[*].text:string · $.payload.role:string · $.type:string', count: 18, first: '09:18:04', latest: '14:31:48', source: 'session-demo-014.jsonl', status: 'UNMAPPED', sample: { timestamp: '2026-09-14T06:31:48.000Z', type: 'response_item', payload: { type: 'future_message', role: 'assistant', content: [{ type: 'output_text', text: 'synthetic preview' }] } } },
  { id: 'fp-c042', signature: '$.payload.call_id:string · $.payload.output[*].text:string · $.type:string', count: 7, first: '11:02:19', latest: '14:29:06', source: 'session-demo-021.jsonl', status: 'MAPPED', sample: { timestamp: '2026-09-14T06:29:06.000Z', type: 'response_item', payload: { type: 'future_tool_output', call_id: 'call_demo_42', output: [{ type: 'input_text', text: 'synthetic tool output' }] } } },
  { id: 'fp-f911', signature: '$.meta.turn:string · $.occurred_at:number · $.type:string', count: 3, first: '13:44:10', latest: '14:08:51', source: 'session-demo-026.jsonl', status: 'FAILED', sample: { occurred_at: 1789366131000, type: 'future_event', meta: { turn: 'turn-demo-026' }, body: { value: 42 } } },
])
const mapping = reactive({ outerType: '$.type', timestamp: '$.timestamp', payloadType: '$.payload.type', turnId: '$.payload.internal_chat_message_metadata_passthrough.turn_id', callId: '$.payload.call_id', role: '$.payload.role', content: '$.payload.content[*].text' })
type MappingKey = keyof typeof mapping
type MappingResult = { key: MappingKey; label: string; path: string; values: unknown[]; error?: string }
const mappingFields: Array<{ key: MappingKey; label: string; multiple?: boolean }> = [
  { key: 'outerType', label: '外层 type' }, { key: 'timestamp', label: 'timestamp' }, { key: 'payloadType', label: 'payload.type' },
  { key: 'turnId', label: 'turn_id' }, { key: 'callId', label: 'call_id' }, { key: 'role', label: 'role' }, { key: 'content', label: '正文', multiple: true },
]
function evaluateJsonPath(root: unknown, path: string): { values: unknown[]; error?: string } {
  if (!path.startsWith('$')) return { values: [], error: '必须以 $ 开始' }
  let nodes: unknown[] = [root], offset = 1
  while (offset < path.length) {
    if (path[offset] === '.') {
      const match = path.slice(offset + 1).match(/^[A-Za-z_][A-Za-z0-9_-]*/)
      if (!match) return { values: [], error: `位置 ${offset + 1} 缺少成员名` }
      const name = match[0]
      nodes = nodes.flatMap((node) => node && typeof node === 'object' && !Array.isArray(node) && name in node ? [(node as Record<string, unknown>)[name]] : [])
      offset += name.length + 1
    } else if (path[offset] === '[') {
      const end = path.indexOf(']', offset)
      if (end < 0) return { values: [], error: '数组选择器缺少 ]' }
      const selector = path.slice(offset + 1, end)
      if (selector === '*') nodes = nodes.flatMap((node) => Array.isArray(node) ? node : [])
      else if (/^(0|[1-9]\d*)$/.test(selector)) nodes = nodes.flatMap((node) => Array.isArray(node) && Number(selector) < node.length ? [node[Number(selector)]] : [])
      else return { values: [], error: `不支持的数组选择器 [${selector}]` }
      offset = end + 1
    } else return { values: [], error: `位置 ${offset} 含不支持的语法` }
  }
  return { values: nodes }
}
const filteredUnknown = computed(() => unknownFilter.value === 'ALL' ? unknownItems : unknownItems.filter((item) => item.status === unknownFilter.value))
const unknownTotal = computed(() => unknownItems.reduce((sum, item) => sum + item.count, 0))
const sampleText = computed(() => selected.value ? JSON.stringify(selected.value.sample, null, 2) : '')
const mappingResults = computed<MappingResult[]>(() => {
  if (!selected.value) return []
  return mappingFields.map((field) => {
    const path = mapping[field.key]
    const result = evaluateJsonPath(selected.value!.sample, path)
    if (result.error) return { ...field, path, values: [], error: result.error }
    if (!field.multiple && result.values.length > 1) return { ...field, path, values: result.values, error: `标量字段匹配了 ${result.values.length} 个值` }
    if (result.values.some((value) => value !== null && typeof value === 'object')) return { ...field, path, values: result.values, error: '匹配值必须是标量' }
    if (field.multiple && result.values.some((value) => typeof value !== 'string')) return { ...field, path, values: result.values, error: '正文只允许字符串' }
    return { ...field, path, values: result.values }
  })
})
const hasMappingErrors = computed(() => mappingResults.value.some((result) => result.error))
function rescan() { scanning.value = true; window.setTimeout(() => { scanning.value = false; ElMessage.success('模拟重试完成：2 个 Hook 会话的 transcript 内容已补齐') }, 800) }
async function copyConfig() { const value = 'Hook command: /workspace/demo-project/bin/trace-lens-hook\nHook receiver: http://127.0.0.1:8080/api/ingestion/hooks\nTrust check: Codex /hooks\nOTLP endpoint: http://127.0.0.1:4318'; try { await navigator.clipboard.writeText(value); copied.value = true; ElMessage.success('安装检查清单已复制') } catch { ElMessage.info('请手动复制安装检查清单') } }
function editUnknown(item: UnknownItem) { selected.value = item; drawerOpen.value = true }
function saveMapping() { if (!selected.value || hasMappingErrors.value) return; selected.value.status = 'MAPPED'; drawerOpen.value = false; ElMessage.success(`已模拟保存 ${selected.value.id}：只重新标准化该指纹的 ${selected.value.count} 条记录`) }
const statusType = (status: UnknownStatus) => status === 'MAPPED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
</script>

<template>
  <main class="page-wrap collection-page v2-collection">
    <section class="hero-row compact-hero"><div><div class="eyebrow">INGESTION HEALTH · V2 BASELINE</div><h1>采集状态</h1><p>Hook 建立执行骨架，JSONL 补齐可见内容，OTel 补齐精确性能；分别检查每一步是否完成。</p></div><div class="live-pill warning-pill"><span class="live-dot" />可用但有兼容性告警</div></section>
    <section class="collector-summary">
      <article class="panel summary-status"><span class="status-icon good"><Check /></span><div><small>核心采集</small><b>运行中</b><em>Hooks · OTel · JSONL</em></div></article>
      <article class="panel summary-status"><span class="status-icon"><DataLine /></span><div><small>Hook 接收</small><b>1,462</b><em>7 条乱序已合并</em></div></article>
      <article class="panel summary-status"><span class="status-icon warn"><FolderOpened /></span><div><small>transcript 关联</small><b>80%</b><em>24 / 30 个文件通过会话校验</em></div></article>
      <article class="panel summary-status"><span class="status-icon warn"><Warning /></span><div><small>UNKNOWN</small><b>{{ unknownTotal }}</b><em>3 种结构指纹</em></div></article>
    </section>
    <section class="collection-grid"><div class="collection-main">
      <article class="panel collector-panel source-contract"><div class="panel-heading"><div><h2>主链路与耗时精度</h2><p>以 Hook 为准，另外两类数据只负责补齐</p></div><el-tag type="info">能力契约</el-tag></div><div class="source-cards"><div><b>1 · HOOK</b><strong>建立执行骨架</strong><span>创建会话、轮次、工具与中断边界，是主事实来源</span></div><div><b>2 · JSONL</b><strong>补齐可见内容</strong><span>通过 transcript_path 补齐正文、参数、结果与原始记录</span></div><div><b>3 · OTEL</b><strong>补齐精确性能</strong><span>为已有骨架补充 API、TTFT、传输与工具 Span</span></div></div><div class="precision-chain"><span>工具耗时优先级</span><b>OTel 精确</b><i>›</i><b>Hook Pre/Post 估算</b><i>›</i><b>JSONL 时间戳估算</b></div></article>
      <article class="panel collector-panel"><div class="panel-heading"><div><h2>Hook 转发链路</h2><p>Codex 执行本地 Hook 命令，forwarder 再调用本机接收接口</p></div><el-tag type="success">接口可用 · 99.98%</el-tag></div><div class="interface-summary"><div><span>最近成功</span><b>4 秒前</b></div><div><span>投递成功率</span><b>99.86%</b><small>1,460 / 1,462</small></div><div><span>处理耗时 P50 / P95 / P99</span><b>7 / 18 / 31 ms</b></div><div><span>最大 / 超时 / 丢弃</span><b>46 ms / 2 / 0</b></div></div><div class="hook-stats"><div><span>队列深度</span><b>3 / 1,000</b></div><div><span>投递延迟 P95</span><b>42 ms</b></div><div><span>Post 先到 / 重复</span><b>7 / 3</b></div><div><span>局部事件</span><b>2</b></div></div><div class="merge-rule"><el-icon><InfoFilled /></el-icon><span>Turn：<code>session_id + turn_id</code></span><span>工具：<code>session_id + turn_id + tool_use_id</code></span><span>状态单调推进</span></div><el-alert title="接口可用不代表 Hook 覆盖完整：forwarder 未启动时后端收不到失败请求，只能显示覆盖未知。" type="info" :closable="false" show-icon /></article>
      <article class="panel collector-panel endpoint-monitor"><div class="panel-heading"><div><h2>OTLP/HTTP 接收链路</h2><p>按信号监控接口可用、实际投递和接收处理开销</p></div><el-tag type="success">readiness 正常</el-tag></div><div class="endpoint-table"><div class="endpoint-row endpoint-head"><span>接口</span><span>最近成功</span><span>投递成功率</span><span>P50 / P95 / P99</span><span>最大</span><span>队列</span><span>失败 / 丢弃</span></div><div class="endpoint-row"><b>Logs <code>/v1/logs</code></b><span>8 秒前</span><strong>99.92%</strong><span>6 / 17 / 29 ms</span><span>44 ms</span><span>8 / 1,000</span><span>1 / 0</span></div><div class="endpoint-row"><b>Traces <code>/v1/traces</code></b><span>12 秒前</span><strong>99.89%</strong><span>11 / 28 / 51 ms</span><span>73 ms</span><span>12 / 1,000</span><span>1 / 0</span></div><div class="endpoint-row idle"><b>Metrics <code>/v1/metrics</code></b><span>最近窗口无请求</span><strong>未知</strong><span>—</span><span>—</span><span>0 / 1,000</span><span>0 / 0</span></div></div><div class="metric-boundary"><el-icon><InfoFilled /></el-icon><span><b>不要混淆：</b>这里的毫秒数是接收、解码、校验和入队耗时，不是模型、工具或业务 Span 的执行耗时。无请求时显示“未知”，不显示虚假的 100%。</span></div></article>
      <article class="panel collector-panel transcript-panel"><div class="panel-heading"><div><h2>transcript_path 会话校验</h2><p>路径安全检查后，校验首行 session_meta.payload.session_id 是否等于 Hook session_id</p></div><strong class="warn-text">6 个需要处理</strong></div><div class="association-score"><div><b>80%</b><span>精确会话关联率</span></div><el-progress :percentage="80" :stroke-width="12" status="warning" /><small>文件首行负责会话校验；后续 JSONL 事件行不要求重复 session_id。</small></div><div class="status-chips"><span class="ok">VALID 24</span><span>EMPTY 1</span><span>MISSING 1</span><span>UNREADABLE 1</span><span>OUTSIDE / SYMLINK 1</span><span>META MISSING 1</span><span>ID MISMATCH 1</span></div><div class="transcript-list"><div class="transcript-head"><span>Hook session_id</span><span>transcript_path</span><span>判定</span><span>session_meta 校验</span></div><div><code>session-demo-021</code><code>/workspace/demo-project/sessions/demo-021.jsonl</code><el-tag size="small" type="success">VALID</el-tag><span>payload.session_id 一致</span></div><div><code>session-demo-024</code><code>/workspace/demo-project/sessions/missing.jsonl</code><el-tag size="small" type="danger">MISSING</el-tag><span>无法读取首行</span></div><div><code>session-demo-030</code><code>/workspace/demo-project/sessions/demo-030.jsonl</code><el-tag size="small" type="danger">ID MISMATCH</el-tag><span>与 Hook session_id 不一致</span></div></div></article>
      <article class="panel collector-panel unknown-panel"><div class="panel-heading"><div><h2>UNKNOWN 原样保留</h2><p>按排序后的叶子 JSONPath + 值类型生成结构指纹</p></div><el-tag type="warning">{{ unknownTotal }} 条原始记录</el-tag></div><div class="unknown-toolbar"><el-radio-group v-model="unknownFilter" size="small"><el-radio-button value="ALL">全部</el-radio-button><el-radio-button value="UNMAPPED">待映射</el-radio-button><el-radio-button value="MAPPED">已映射</el-radio-button><el-radio-button value="FAILED">失败</el-radio-button></el-radio-group><span>数组元素以 <code>[*]</code> 归一化</span></div><div class="unknown-list"><article v-for="item in filteredUnknown" :key="item.id"><div><code>{{ item.id }}</code><el-tag size="small" :type="statusType(item.status)">{{ item.status }}</el-tag><b>{{ item.count }} 条</b></div><p>{{ item.signature }}</p><small>{{ item.source }} · 首次 {{ item.first }} · 最近 {{ item.latest }}</small><el-button size="small" :icon="Setting" @click="editUnknown(item)">{{ item.status === 'MAPPED' ? '查看映射' : '配置解析' }}</el-button></article></div></article>
      <article class="panel collector-panel"><div class="panel-heading"><div><h2>Hook 内容补齐（JSONL）</h2><p>只解析已通过 session_meta 会话校验的 transcript，为 Hook 骨架补齐正文</p></div><el-tag type="warning">2 个待补齐</el-tag></div><div class="collector-path"><el-icon><FolderOpened /></el-icon><code>Hook transcript_path → /workspace/demo-project/sessions/demo-021.jsonl</code><span>session_meta 已一致</span></div><div class="collector-stats"><div><span>Hook 会话骨架</span><b>30</b></div><div><span>会话校验通过</span><b>24</b></div><div><span>内容已补齐</span><b>22</b></div><div><span>等待文件写入</span><b>2</b></div></div><div class="scan-progress"><span>已验证 transcript 内容补齐率 92%</span><el-progress :percentage="92" status="warning" /><el-button :icon="Refresh" :loading="scanning" @click="rescan">重试待补齐内容</el-button></div><div class="rescan-explainer"><el-icon><InfoFilled /></el-icon><span><b>这一步的目的：</b>Hook 建立执行骨架；JSONL 首行 session_meta 先确认文件属于同一会话，随后才解析正文、模型输出、工具参数和结果。没有 Hook 骨架或会话 ID 不一致的文件不会进入正式分析。</span></div></article>
    </div><aside class="collection-aside">
      <article class="panel health-check-panel"><div class="panel-heading"><div><h2>独立健康项</h2><p>最近判定 · 4 秒前</p></div></div><ul><li><span class="live-dot" /><b>Hook 骨架接收</b><em>4s &lt; 30s</em></li><li><span class="live-dot" /><b>OTel 性能补齐</b><em>失败率 0.04%</em></li><li class="warning-row"><el-icon><Warning /></el-icon><b>JSONL 内容补齐</b><em>22 / 24</em></li><li class="warning-row"><el-icon><Warning /></el-icon><b>session_meta 校验</b><em>80% · 告警</em></li><li><span class="live-dot" /><b>SQLite</b><em>最近事务成功</em></li></ul></article>
      <article class="panel config-panel"><div class="panel-heading"><div><h2>配置向导</h2><p>按当前 Codex 官方 schema 生成，不硬编码示意 TOML</p></div><el-tag type="warning" effect="plain">演示</el-tag></div><pre>Hook command
/workspace/demo-project/bin/trace-lens-hook

Receiver
http://127.0.0.1:8080/api/ingestion/hooks

Trust check
Codex /hooks</pre><el-button class="copy-button" :icon="copied ? Check : CopyDocument" @click="copyConfig">{{ copied ? '已复制' : '复制安装检查清单' }}</el-button><p class="config-note">安装器按检测到的 Codex 版本生成官方格式；页面不臆造配置语法，也不自动修改 <code>~/.codex/config.toml</code>。</p></article>
      <article class="panel id-panel"><h2>ID 不是一回事</h2><dl><dt>Hook session_id</dt><dd>会话标识</dd><dt>Hook tool_use_id</dt><dd>Hook 工具调用键</dd><dt>JSONL payload.call_id</dt><dd>JSONL 调用关联键</dd></dl><p>后二者属于不同命名空间；只有验证值和语义稳定一致时才算精确证据。</p></article>
    </aside></section>
    <el-drawer v-model="drawerOpen" size="620px" class="mapping-drawer" title="UNKNOWN 字段映射"><template v-if="selected"><div class="drawer-intro"><el-tag type="warning">{{ selected.id }}</el-tag><span>{{ selected.count }} 条记录将被重新标准化</span></div><el-alert title="你的操作：填写 JSONPath → 检查实际匹配数量、类型和值 → 保存 → 只重处理当前指纹。支持 $、对象成员、数组下标与 [*]。" :type="hasMappingErrors ? 'error' : 'info'" :closable="false" show-icon /><div class="mapping-layout"><section><h3>JSONPath 映射</h3><el-form label-position="top" size="small"><el-form-item label="外层 type"><el-input v-model="mapping.outerType" /></el-form-item><el-form-item label="timestamp"><el-input v-model="mapping.timestamp" /></el-form-item><el-form-item label="payload.type"><el-input v-model="mapping.payloadType" /></el-form-item><el-form-item label="turn_id"><el-input v-model="mapping.turnId" /></el-form-item><el-form-item label="call_id"><el-input v-model="mapping.callId" /></el-form-item><el-form-item label="role"><el-input v-model="mapping.role" /></el-form-item><el-form-item label="正文（可多值）"><el-input v-model="mapping.content" /></el-form-item></el-form><small>示例：<code>$.payload.content[*].text</code></small></section><section><h3>实时求值与校验</h3><div v-for="result in mappingResults" :key="result.key" :class="['mapping-result', { invalid: result.error }]"><div><b>{{ result.label }}</b><small>{{ result.values.length }} 个匹配</small></div><code>{{ result.path }}</code><span v-if="result.error">{{ result.error }}</span><span v-else-if="result.values.length">{{ result.values.join('\n') }}</span><span v-else>未匹配（允许为空）</span></div><h3>合成原始样本</h3><pre class="raw-sample">{{ sampleText }}</pre></section></div><div class="drawer-footer"><span>保存不修改原始 JSON，也不触发 Hook 重放或全量补扫。</span><el-button @click="drawerOpen = false">取消</el-button><el-button type="primary" :disabled="hasMappingErrors" @click="saveMapping">保存并重新标准化</el-button></div></template></el-drawer>
  </main>
</template>
