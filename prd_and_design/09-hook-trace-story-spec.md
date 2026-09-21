# Hook 采集与 Trace Story 规格

> 本文记录已完成 E1-S1 的历史验收契约。E1-S2.2 已选择 OTel + Transcript、OTel-first；Hook 主链路不再是目标架构，现有实现只在迁移完成前用于回归和回滚。

## Story 信息

- 编号：E1-S1。
- 名称：真实 Codex Hook 主链路。
- 优先级：P0。
- 状态：开发完成；用户于 2026-09-16 明确接受。
- 前置门禁：E0-S0 领域化架构升级已完成，并已通过 Story1 主链路回归。
- 人工门禁：已满足；后续 Story 仍须按各自的独立规格、测试用例和用户启动指令推进。

## 用户价值

作为本机 Codex 用户，我希望在发起一个真实 Codex 轮次后，无需修改 Codex 源码，就能在 Trace Lens 中看到该轮次的用户入口、工具 Pre/Post 和最终状态，并能检查 Hook 原始证据，从而确认核心采集链路真实可用。

## 本 Story 范围

### 包含

- 使用 Codex 0.154.0 已验证的 `hooks.json` 事件集合。
- 使用 uv 运行 Python 3.11 forwarder；完整保留 Hook stdin，封装 `deliveryId` 和 `observedAt`。
- 向仅回环监听的 `POST /api/ingestion/hooks` 投递，写入 MySQL 原始 Hook 和标准化任务。
- 按 `(session_id, turn_id)` 和 `(session_id, turn_id, tool_use_id)` 建立骨架。
- 通过 `GET /api/sessions` 和 `GET /api/sessions/{turnId}/analysis` 查询，在正式 Vue 前端显示 Trace 行为节点。
- 手工验收配置、Hook 信任、真实 Codex 轮次和页面证据。

### 不包含

- 本用例不宣称 API、TTFT、审批或本地处理耗时已完整实现。
- 不以 Hook 时间伪造 TTFT，不将时间接近伪装成精确跨源关联。
- 不自动修改 `~/.codex/config.toml` 或信任 Hook。
- transcript 完整内容补齐、OTel 精确性能和异常降级分别在 E1-S2、S3 和 S5 验收。
- CLI 与后端本地运行日志已登记为后续高优先级 E1-S1.1（强关联 S1）；前端日志暂不纳入范围。
- OTel 精确关联的服务层重构已登记为 E1-S3.1（强关联 S1）；当前 S1 不实现或验收该能力。

## 接口契约

### forwarder 请求

```text
POST http://127.0.0.1:8080/api/ingestion/hooks
Content-Type: application/json
X-Trace-Lens-Forwarder-Version: 0.1.0

{
  schemaVersion,
  deliveryId,
  observedAt,
  forwarderVersion,
  rawEvent
}
```

- 成功新投递返回 `202 ACCEPTED`，重复 `deliveryId` 返回 `200 DUPLICATE`。
- forwarder 连接超时 250ms，总超时 1s；仅对连接失败、超时和 5xx 立即重试一次。
- 任何结果都退出 0，不将原始输入写入普通日志，不阻断 Codex。

### 查询契约

- `GET /api/ingestion/hooks/status`：验证最近成功时间、请求数、接收数和重复数。
- `GET /api/sessions?limit=200`：按用户问题和 Turn 标识找到本次轮次。
- `GET /api/sessions/{turnId}/analysis`：返回 `session`、`agentEvents`、`toolCalls`、`performanceSpans`、`alignments`、`diagnoses` 和 `aggregates`。
- `GET /api/events`：SSE 能力已存在，但运行中到完成的人工验收归 E1-S4。

## 主数据流

```text
Codex lifecycle event
  -> hooks.json command
  -> cli/trace_lens_hook.py
  -> POST /api/ingestion/hooks
  -> raw_hook_event + normalization_job
  -> HookNormalizationWorker
  -> hook_session / hook_turn / hook_tool_call
  -> /api/sessions + /analysis
  -> frontend apiAdapter
  -> TracePage + TimelineChart + EventInspectorDrawer
```

## 一致性门禁（2026-09-15）

| 检查面 | 结论 | 处理 |
| --- | --- | --- |
| PRD 与 V2 原型 | Hook-first、三源证据、缺源降级一致 | 无产品语义阻塞，不需要 V3 |
| PRD 与技术设计 | Hook 命令回调、仅回环接收、MySQL 和异步标准化一致 | 无阻塞 |
| 官方 Hook 契约与 fixture | 本机 `codex-cli 0.154.0`；事件集合和字段基线已锁定 | 首次人工验收仍必须在 `/hooks` 审查与信任 |
| Python 环境 | 系统 `python3` 是 3.14.3，不符合仓库规则；`cli/pyproject.toml` 固定 Python 3.11，已验证 uv 解析为 3.11.15 | 正式 Hook 命令必须使用 `uv run --project <repo>/cli`，不使用裸 `python`/`python3` |
| 当前 `cli/hooks.json` | 命令 `python ./trace_lens_hook.py` 依赖会话 cwd，且 Python 版本不合规 | 不把该未跟踪文件作为正式配置；按集成测试文档生成项目级配置 |
| 当前 Trace 能力 | Hook 行为骨架已有实现；当前工具 OTel 关联把未由真实 Codex 样本验证的 `Hook.tool_use_id == OTel codex.call_id` 直接标为 EXACT | S1 验收只宣称 Hook 骨架主链路，并在无 OTel 时显示缺失；S3.1 必须先重构并验证关联契约，才可宣称工具 OTel 精确关联 |
| 运行文档 | 原 `backend/README.md` 仍声称 SQLite/B1，与实现冲突 | 本次同步修正，不改业务实现 |

## 验收标准

1. 用户在项目级 `/hooks` 看到本项目 Hook，审查实际命令后手工信任。
2. 从新 Codex 会话提交固定人工测试提示，至少产生 `UserPromptSubmit`、`PreToolUse`、`PostToolUse` 和 `Stop`。
3. Hook 健康接口的 accepted 计数增加，且没有因 forwarder 导致 Codex 任务失败。
4. 分析总览只出现一个对应 Turn，用户问题、会话 ID、Turn ID 和状态可识别。
5. Trace 页可见用户入口、工具 Pre/Post 和最终状态，点击节点可检查 Hook 原始证据。
6. 未完整的 transcript 或 OTel 能力必须显示缺失/部分，不得显示伪造的 TTFT 或精确关联。
7. 用户完成代码阅读，能口述上述数据流、幂等键和“Hook 只建骨架”的边界。

## 代码阅读路线

1. `cli/generate_hooks.py`：配置覆盖哪些官方事件，为什么 SessionEnd/Interrupt 超时更短。
2. `cli/trace_lens_hook.py`：原始 stdin 如何被封装，重试和“永返回 0”如何避免阻断 Codex。
3. `backend/.../api/IngestionController.java`：接口大小、JSON 和错误边界。
4. `backend/.../ingestion/HookIngestionService.java`：原始 Hook 和标准化任务的事务与 `deliveryId` 幂等。
5. `backend/.../ingestion/HookNormalizationWorker.java`：Session/Turn/Tool 复合键、状态推进与异步处理。
6. `backend/.../mappers/IngestionMapper.xml`：MySQL upsert、唯一键和查询证据。
7. `backend/.../api/AnalysisController.java`：从行为骨架组装 Trace 返回，以及当前尚未完整的耗时类别。
8. `frontend/src/data/apiAdapter.ts` → `TracePage.vue` → `TimelineChart.vue` → `EventInspectorDrawer.vue`：线上数据如何映射、绘制和显示原始证据。

## Definition of Done

- 本 Story 范围内的单元测试通过。
- `IT-HOOK-TRACE-001` 由用户手工执行并通过，证据写入 `07-main-flow-acceptance.md`。
- 用户完成代码阅读并明确接受 Story。
- 无真实凭据、本机路径、真实会话或测试日志被提交。
- 用户明确决定是否启动下一项 E1-S1.1；未经指令不得启动。
