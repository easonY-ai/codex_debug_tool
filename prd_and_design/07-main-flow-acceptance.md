# Hook/Trace 主链路集成测试与验收记录

> 本文的 `IT-HOOK-TRACE-*` 是已验收 Hook-first 历史基线。用户于 2026-09-18 确认 V1.0 选择 OTel + Transcript、OTel-first，且不要求逐事件复刻 Hook；这些用例用于迁移回归和差异比较，不作为目标架构的等价性验收。代码迁移前必须新增 OTel→Execution→Transcript→Trace 主链路用例，并保留工具 `EXACT` 的版本化样本门禁。

## OTel + Transcript 目标主链路用例（计划，未授权执行）

| 编号 | 场景 | 关键步骤 | 通过标准 |
| --- | --- | --- | --- |
| `IT-OTEL-TRACE-001` | 普通回复 | 启用仅回环 OTLP exporter，产生一个无工具 Turn，等待 Transcript 增量读取并打开 Trace | OTel `conversation.id/turn.id` 建立唯一 Session/Turn；Transcript 相同身份补齐输入与最终输出；无 Hook 投递；API/TTFT 只来自 OTel |
| `IT-OTEL-TRACE-002` | 单工具成功 | 产生一个明确工具调用，检查 OTel Tool 事件/Span 与 Transcript `call_id` | 两侧先精确落入同一 Turn；只有版本化公共调用 ID 才显示 `EXACT`，否则显示 `INFERRED/UNMATCHED`；工具耗时使用 OTel |
| `IT-OTEL-TRACE-003` | 工具失败或中断 | 产生失败或缺失终态的工具场景 | 原始两侧证据保留；Turn/Tool 不被误标成功；缺失终态明确显示不完整 |
| `IT-OTEL-TRACE-004` | 串行、重试与单调用内并行子执行 | 同 Turn 产生两个串行模型 Tool Call、失败后重试，以及一个包含多个并行 `CommandExecution` 的模型 Tool Call | 串行与重试按不同模型 Call ID 分开；并行场景展示一个父 Tool Call 和 N 个子执行，计数分别为 1/N，父耗时不得对子执行耗时求和；缺少已验证公共 ID 的其他候选保持 `INFERRED/UNMATCHED` |
| `IT-OTEL-TRACE-005` | OTel 缺失 | 禁用 exporter，仅产生 Transcript | 只显示 Transcript 来源健康、原始记录和内容检查；不创建正式性能 Trace，不用 Transcript 伪造 API/TTFT |
| `IT-OTEL-TRACE-006` | Transcript 缺失 | 启用 OTel，但关闭或阻断 Transcript 读取 | 正式执行与性能 Trace 可用，内容明确缺失；采集健康区分 OTel 成功与 Transcript 失败 |
| `IT-OTEL-TRACE-007` | 重复、乱序与延迟 | 重放合成 OTLP 批次并延迟 Transcript Item | Execution 幂等；状态不回退；Trace 通过 Change Feed 重算收敛，节点和内容不重复 |
| `IT-OTEL-TRACE-008` | 审批决策但无等待边界 | 输入带批准或拒绝决策、但没有可验证等待起止的版本化 OTel fixture | 展示决策、来源和事件时间；审批等待耗时为未知，未覆盖时间只计入未归因；不生成审批等待诊断 |

这些用例必须使用合成 fixture 或人工本机验证，真实载荷、路径、账号和凭据不得进入仓库。`IT-OTEL-TRACE-002` 至 `004` 的工具契约未完成版本化采样前只能保持计划状态，不能用假定字段写实现测试。

## 执行策略

- `IT-HOOK-TRACE-001` 与 `IT-HOOK-TRACE-002` 均已完成，并作为后续架构升级和功能 Story 的回归基线。
- 当前没有获授权执行的新集成测试；S2.1-S1 设计基线已完成并等待用户确认，完成架构迁移后必须回归既有主链路，且其验收前不开始 S3/S3.1。
- 历史自动化结果只作为回归基线，不代替本次真实 Codex 人工验收。
- 真实数据只留在本机，不得将会话、凭据、本机路径或日志提交到公开仓库。

## 人工环境准备

### 1. 前置检查

在仓库根目录执行：

```bash
codex --version
java -version
uv run --project cli python --version
```

已知基线是 Codex CLI 0.154.0、JDK 17 和由 uv 解析的 Python 3.11。如 Codex 版本已变化，先更新 Hook fixture 与契约。

### 2. 生成项目级 Hook 配置

从仓库根目录执行：

```bash
mkdir -p .codex
uv run --project cli python cli/generate_hooks.py \
  --command 'uv run --project "$(git rev-parse --show-toplevel)/cli" python "$(git rev-parse --show-toplevel)/cli/trace_lens_hook.py"' \
  --output .codex/hooks.json
```

这会生成项目级 `.codex/hooks.json`，不修改 `~/.codex/config.toml`。命令通过 Git 根目录定位 forwarder，不依赖 Codex 启动时的子目录。不要使用当前未跟踪的 `cli/hooks.json`，其中相对命令依赖特定 cwd。

在 Codex 中输入 `/hooks`：

1. 确认来源是当前项目的 `.codex/hooks.json`。
2. 审查实际命令与事件列表。
3. 手工信任该 Hook。
4. 如同一配置层还有内联 `[hooks]`，先移除重复表达，避免重复投递。

Codex 只在项目配置层受信任后加载项目 Hook，修改 Hook 后需重新审查；见 [OpenAI 官方 Hooks 文档](https://learn.chatgpt.com/zh-Hans/docs/hooks)。

### 3. 启动后端

在当前 shell 设置 `MYSQL_USERNAME` 和 `MYSQL_PASSWORD`，但不把凭据写进命令、文档或日志。然后在仓库根目录执行：

```bash
./package.sh
./run.sh \
  --analyzer.jsonl.enabled=true \
  --analyzer.jsonl.root="$HOME/.codex"
```

`package.sh` 先执行前端单元测试与构建，再执行后端 `verify` 并生成包含正式前端的 JAR。如本轮已对当前提交成功执行过 `package.sh`，可直接运行 `run.sh`。

验证：

```bash
curl -sS http://127.0.0.1:8080/api/ingestion/status
curl -sS http://127.0.0.1:8080/api/ingestion/hooks/status
```

如 `CODEX_HOME` 不是 `~/.codex`，将 `analyzer.jsonl.root` 改为实际根目录；该目录必须包含 `sessions` 子目录。

### 4. 启动正式前端

另开终端：

```bash
cd frontend
npm run dev -- --port 4173
```

打开 `http://127.0.0.1:4173/`。

## IT-HOOK-TRACE-001：真实 Codex Hook 到 Trace 骨架

### 目标

验证真实 Codex 事件经项目 Hook、Python forwarder、Java 接收器、MySQL、查询 API 和正式 Vue 前端后，在 Trace 页建立行为骨架。

### 手工步骤

1. 记录 `GET /api/ingestion/hooks/status` 中当前 `accepted` 计数。
2. 确认 `/hooks` 中项目 Hook 已信任，然后新建一个 Codex 会话，以同时覆盖 SessionStart。
3. 在新会话中发送固定提示：

   ```text
   请使用 Bash 执行 printf 'trace-lens-manual-001\n'，然后只回复 trace-lens-manual-001。
   ```

4. 等待 Codex 完成，确认 Hook 未阻断轮次，最终回复是 `trace-lens-manual-001`。
5. 再次查询 Hook 状态，确认 `accepted` 至少增加 4，且 `lastSuccessAt` 已更新。
6. 在分析总览搜索 `trace-lens-manual-001`，确认只有一个对应执行轮次且状态为完成。
7. 点击该行进入 Trace，展开“可访问的时间轴节点列表”，确认至少有 `UserPromptSubmit`、`PreToolUse`、`PostToolUse` 和 `Stop`。
8. 点击 `PreToolUse` 和 `PostToolUse`，检查 Hook 原始证据中的 `session_id`、`turn_id`、`tool_use_id`、`tool_name`；两个工具边界应使用同一 `tool_use_id`。
9. 查看完整度与性能层。如本轮没有 OTel，必须显示缺失/部分，TTFT 不得凭 Hook 时间生成。
10. 按 `09-hook-trace-story-spec.md` 的顺序阅读代码，对照真实 ID 理解数据流。

### 通过标准

- 步骤 1–10 全部通过。
- 工具 Hook 节点不重复，Pre/Post 关联为同一工具调用。
- 页面结论与可检查的原始 Hook 证据一致。
- 任何缺失源都诚实降级，不伪造性能数据或精确关联。

### 失败时记录

只提供失败步骤、`/hooks` 来源/信任状态/错误摘要、脱敏的 Hook status 计数、浏览器错误和 HTTP 状态码；不粘贴数据库密码、完整 transcript 或其他真实对话。

## IT-HOOK-TRACE-002：真实 Transcript 内容补齐

### 执行条件

- S2 自动单元测试、JDK 17/MySQL 集成测试和正式前端测试已经通过并记录。
- 使用“人工环境准备”中的 JDK 17、uv Python 3.11、项目级受信任 Hook、独立本机 MySQL 和正式前端。
- `analyzer.jsonl.enabled=true`，`analyzer.jsonl.root` 指向当前 Codex 数据根目录；不得把实际根目录、日志或 transcript 内容写入仓库。

### 目标

验证真实 Codex 事件先由 Hook 建立 Session/Turn/Tool 骨架，再由该 Hook 的 `transcript_path` 定位同一会话 JSONL，经路径和 `session_meta` 校验后补齐用户输入、工具参数/结果和最终可见输出，并在 Trace 检查三源证据与缺源降级。

### 手工步骤

1. 查询 `GET /api/ingestion/hooks/status` 与 `GET /api/ingestion/transcripts/status`，记录脱敏计数，不复制路径或完整 ID。
2. 在 `/hooks` 确认当前项目 Hook 已信任；新建 Codex 会话以产生新的 SessionStart 和 transcript。
3. 在新会话发送固定提示：

   ```text
   请使用 Bash 执行 printf 'trace-lens-transcript-002\n'，然后只回复 trace-lens-transcript-002。
   ```

4. 等待完成，确认 Hook/日志没有阻断 Codex，最终回复为 `trace-lens-transcript-002`。
5. 再次查询 transcript 状态，确认本次会话绑定的 `path_status=VALID`、`session_check_status=MATCHED`，并显示适配器版本；页面和检查记录不得暴露完整本机路径。
6. 在分析总览搜索 `trace-lens-transcript-002`，确认只有一个 Hook 建立的对应 Turn；JSONL 不得额外创建第二个会话或 Turn。
7. 进入 Trace，点击用户入口节点，确认“输入”来自 JSONL 补齐，并可分别检查 Hook 原始证据和对应 JSONL 原文；两侧 `turn_id` 属于同一轮次。
8. 点击工具调用节点，确认可见 Bash 参数包含固定 `printf` 命令；点击工具结果节点，确认结果包含 `trace-lens-transcript-002`。进入工具节点的“关联证据”页签：若页面声明 `EXACT`，必须显示适配器版本、Hook `tool_use_id` 和 JSONL `call_id` 的相等证据；标识不同时必须显示 `BOUNDED`，不得声明精确关联。
9. 点击最终状态/模型输出节点，确认最终可见输出为 `trace-lens-transcript-002`，并能查看来源 JSONL 原文；不得展示模型未公开思维链。
10. 查看完整度和性能层：transcript 与内容覆盖应已提升；如未配置 OTel，TTFT/API/精确性能仍必须显示缺失，不得使用 JSONL 或 Hook 伪造。
11. 手动触发一次 `POST /api/ingestion/rescan` 或等待下一次补扫，刷新同一 Trace，确认同一原始记录和内容节点没有重复。
12. 按 `12-transcript-content-story-spec.md` 的代码阅读路线检查 Scheduler、Use Case、Domain、Repository、Jackson/FileSystem 适配器、查询 DTO 与正式前端映射，确认 Mapper 只做简单读写。

### 通过标准

- 步骤 1–12 全部通过。
- Hook 是唯一骨架来源；JSONL 只在安全路径和 session ID 匹配后补内容。
- 用户输入、工具参数、工具结果和最终输出都能从对应 Hook 节点检查到 JSONL 证据。
- 重扫幂等，内容节点不重复。
- JSONL/Hook/OTel 的来源、关联等级和缺失状态没有被混淆。

### 失败时记录

只记录失败步骤、绑定状态枚举、脱敏计数、HTTP 状态码和页面错误摘要。不得粘贴 transcript 路径、完整 ID、原始 JSONL、真实对话、数据库凭据或日志正文。

## 后续集成测试清单（未授权执行）

| 编号 | 范围 | 关键预期 | 状态 |
| --- | --- | --- | --- |
| IT-HOOK-TRACE-002 | transcript 内容补齐 | session_meta 匹配后补齐用户、工具和最终内容 | 步骤 1–12、代码阅读与用户验收均已通过 |
| IT-HOOK-TRACE-003 | OTel 工具性能 | 共享已验证 ID 时为 EXACT，耗时摘要守恒 | 待 S2.1 验收且 S3.1 完成 |
| IT-HOOK-TRACE-004 | 实时 SSE | 运行中转完成，节点可检查 | 待 S3 验收 |
| IT-HOOK-TRACE-005 | 重复与乱序 | 重复 delivery 去重，Post 先到可补齐，终态不回退 | 待 S4 验收 |
| IT-HOOK-TRACE-006 | 无效耗时 | 反向/负耗时不进入统计 | 待 S4 验收 |
| IT-HOOK-TRACE-007 | transcript 安全与错配 | 越界、符号链接、不可读、session ID 错配均不挂接 | 待 S4 验收 |
| IT-HOOK-TRACE-008 | 半行与重扫 | 不消费未完整行，完成后只导入一次 | 待 S4 验收 |
| IT-HOOK-TRACE-009 | UNKNOWN 映射 | 受限 JSONPath 校验，只重处理当前指纹 | 待 S4 验收 |
| IT-HOOK-TRACE-010 | 缺失来源降级 | 无 OTel 时 TTFT 未知；无 Hook 时不生成正式 Turn | 待 S4 验收 |
| IT-HOOK-TRACE-011 | 同名并行工具 | 无公共 ID 时保持歧义，不提升为 EXACT | 待 S4 验收 |
| IT-HOOK-TRACE-012 | forwarder 故障 | 4xx/5xx/超时/无后端时均退出 0，不泄露原始输入 | 待 S4 验收 |

## 执行记录

| 日期 | 用例 | 执行者 | 结果 | 证据/备注 |
| --- | --- | --- | --- | --- |
| 2026-09-15 | CLI 单元测试 | Codex | 通过 | 4/4；不依赖网络、MySQL 或真实 Codex 数据 |
| 2026-09-15 之前 | 合成数据自动 E2E | 自动化 | 历史通过 | Git 提交与现有 E2E 文件记录 2 项通过；不代替本轮人工验收 |
| 2026-09-15 | IT-HOOK-TRACE-001 | 用户 | 未通过 | 步骤 6 在分析总览找到了对应轮次，但执行轮次表格未展示状态，无法确认为完成；已进入 S1 实现错误修复 |
| 2026-09-15 | 步骤 6 状态展示修复 | Codex | 自动验证通过 | 先增加失败用例，再在总览表格展示成功、失败、运行中；前端 4 个测试文件、18 项通过，生产构建通过；等待用户复验 |
| 2026-09-15 | IT-HOOK-TRACE-001 步骤 1–6 | 用户 | 通过 | 状态展示修复后完成人工复验；下一步执行步骤 7 |
| 2026-09-15 | IT-HOOK-TRACE-001 步骤 7–9 | 用户 | 通过 | Trace 行为节点、原始 Hook 证据与缺源降级展示均通过；下一步是步骤 10 代码阅读 |
| 2026-09-15 | S1 代码阅读 bugfix：原始 Hook 主键回填 | Codex | 自动验证通过 | 实现错误，不改变需求或技术方案：MyBatis 使用 MySQL 生成键回填，移除插入后按 `deliveryId` 查询 ID 的额外往返；新增真实 MySQL 集成测试，后端全量 28 项测试通过 |
| 2026-09-15 | IT-HOOK-TRACE-001 步骤 1–10 | 用户 | 通过 | 用户已完成全部手工步骤和代码阅读；代码阅读发现的 OTel 精确关联契约问题已登记为后续 S3.1，不属于 S1 验收范围 |
| 2026-09-16 | IT-HOOK-TRACE-001 步骤 1–10（Story0 回归） | 用户 | 通过 | 领域化架构升级后重新检查步骤 1–10，通过；该记录是 Story0 对 S1 主链路的人工回归证据 |
| 2026-09-16 | E1-S1 真实 Codex Hook 主链路 | 用户 | 接受 | `IT-HOOK-TRACE-001` 全部步骤和代码阅读完成，用户明确确认“S1 OK”；Story 结束 |
| 2026-09-16 | E1-S2 自动回归 | Codex | 通过 | 后端 JDK 17/MySQL `clean verify` 47 项通过；前端 5 个测试文件、19 项通过；生产构建通过；进入 `IT-HOOK-TRACE-002` 人工验收 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 8 | 用户 | 未通过 | 页面未在“关联证据”中显示 Transcript 的 `EXACT/BOUNDED` 说明及公共 ID 证据；进入 S2 展示实现修复 |
| 2026-09-16 | S2 步骤 8 关联证据展示修复 | Codex | 自动验证通过 | 工具节点现明确显示 Transcript 关联等级、适配器版本及两侧 ID；前端 5 个测试文件、19 项通过，生产构建通过；等待用户复验步骤 8 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 8 复验 | 用户 | 未通过 | JSONL `call_id` 与 Hook `tool_use_id` 不同；PostToolUse 已按 `BOUNDED` 补齐，但 PreToolUse 未补齐 |
| 2026-09-16 | S2 不同内部 turn_id 工具配对修复 | Codex | 自动验证通过 | 真实格式显示同一 JSONL `call_id` 的工具输入/输出可携带不同内部 `turn_id`；新增唯一 Hook Turn 候选规则和歧义拒绝用例，JDK 17/MySQL 全量 49 项通过；等待用户再次复验步骤 8 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 9 | 用户 | 未通过 | Stop 节点没有 JSONL 内容或关联；真实 transcript 的最终可见输出位于 `task_complete.last_agent_message`，当前适配器未覆盖 |
| 2026-09-16 | S2 task_complete 最终输出适配修复 | Codex | 自动验证通过 | `task_complete.last_agent_message` 作为同 Turn 的 `MODEL_OUTPUT/BOUNDED` 补齐 Stop；JDK 17/MySQL 全量 49 项通过，等待用户复验步骤 9 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 1–10 | 用户 | 通过 | 用户在工具配对和 Stop 最终输出修复后确认步骤 1–10 均通过 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 11 | 用户 | 自动检查通过，待页面确认 | 连续两次执行增量补扫，均扫描 52 个文件、写入 0 条、失败 0；等待刷新同一 Trace 并确认内容节点无重复 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 11 页面复验 | 用户 | 通过 | 用户刷新同一 Trace 后确认内容节点无重复；结合两次零新增、零失败补扫，步骤 11 完成 |
| 2026-09-16 | IT-HOOK-TRACE-002 步骤 12 | 用户 | 通过 | 已按 S2 代码阅读路线完成 Scheduler、Use Case、Domain、Repository、基础设施适配器、查询 DTO 与正式前端映射检查 |
| 2026-09-16 | E1-S2 Transcript 内容补齐 | 用户 | 接受 | `IT-HOOK-TRACE-002` 步骤 1–12、代码阅读和自动回归全部完成，用户明确确认接受 S2；Story 结束 |

## 当前结论

E1-S1、E1-S1.1 与 E1-S2 均已完成并由用户接受。`IT-HOOK-TRACE-002` 步骤 1–12、代码阅读和自动回归全部通过。S2.1-S1 设计基线已完成并等待用户确认；尚未授权新的集成测试或业务代码修改。架构迁移完成后必须重新执行受影响的 S1、S1.1、S2 自动与人工回归。
