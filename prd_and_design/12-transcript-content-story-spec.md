# Story2：Transcript 内容补齐

## Story 信息

- 编号：E1-S2。
- 名称：Transcript 内容补齐。
- 优先级：P0。
- 状态：开发完成；用户于 2026-09-16 完成代码阅读并明确接受。
- 启动记录：用户于 2026-09-16 明确启动。
- 前置：E1-S1 与 E1-S1.1 已由用户验收完成。
- 验收记录：领域化重构、自动回归和 `IT-HOOK-TRACE-002` 步骤 1–12 均已完成，用户已明确接受 S2。

## 用户价值

作为本机 Codex 用户，我希望 Hook 建立的轮次和工具骨架能够从该 Hook 指向的 transcript 中补齐用户输入、工具参数、工具结果和最终可见输出，并且能检查文件绑定与原始 JSONL 证据，从而在不伪造跨源关系的前提下理解该轮次做了什么。

## 范围

包含：

- 校验 Hook `transcript_path` 的根目录、`sessions` 子树、普通文件、可读性与符号链接边界；
- 校验首条完整 JSONL 的 `session_meta.payload.session_id`；
- 使用版本化适配器解析已知用户、模型、reasoning summary、工具调用和工具结果记录；
- 按 Hook Session/Turn/Tool 骨架挂接内容及原始 JSONL 证据；
- 返回结构化 transcript 状态和 Trace 内容 DTO，并在正式前端显示；
- 通过人工真实 Codex 主链路验证。

不包含：

- JSONL 独立发现或创建正式 Session/Turn/Tool；
- UNKNOWN 人工 JSONPath 映射的完整验收，该能力归 E1-S5；
- OTel 精确性能、TTFT 或 API 耗时，该能力归 E1-S3/S3.1；
- 通过时间接近把 JSONL `call_id` 与 Hook `tool_use_id` 提升为精确关系；
- 修改冻结 V1/V2 原型。

## 领域与分层设计

```text
interfaces/transcriptcontent/TranscriptContentScheduler
  -> application/transcriptcontent/SupplementTranscriptContentUseCase
  -> TranscriptSourceGateway + TranscriptRecordParser
  -> domain/transcriptcontent/TranscriptBinding + TranscriptContentSupplement
  -> TranscriptBindingRepository + JsonlSupplementRepository
  -> infrastructure/transcriptcontent 文件系统/Jackson/MyBatis 适配器
```

- Scheduler 只触发用例。
- Application 负责扫描、跨 Repository 编排和事务边界。
- Domain 负责路径/会话校验后的状态、不变量、内容分类和关联等级。
- Infrastructure 负责文件系统、JSON 和数据库细节。
- Repository 按绑定、原始记录和补齐实体拆分；不得继续通过通用 `IngestionMapper` 向 Application 暴露 `Map<String,Object>`。
- Controller 返回明确 DTO；业务入口按 S1.1 规则记录白名单摘要，不记录 transcript 路径、正文或原始 JSON。

## 绑定不变量

1. 配置为空时为 `EMPTY/NOT_CHECKED`。
2. 请求路径必须位于配置根目录的 `sessions` 子树内；任一路径分量为符号链接或最终真实路径越界时为 `OUTSIDE_ROOT_OR_SYMLINK/NOT_CHECKED`。
3. 缺失文件为 `MISSING/NOT_CHECKED`，非普通文件或不可读为 `UNREADABLE/NOT_CHECKED`。
4. 找不到已采集的首条完整记录时为 `VALID/SESSION_META_MISSING`。
5. 首条记录不是已支持 `session_meta` 或缺少文本会话 ID 时为 `VALID/SESSION_META_UNSUPPORTED`。
6. JSONL 会话 ID 与 Hook 会话 ID 不同为 `VALID/SESSION_ID_MISMATCH`，不得挂接任何内容。
7. 只有 `VALID/MATCHED` 才能处理后续记录。

## 已知内容契约

适配器版本 `codex-2026-09` 支持以下结构化输出：

| JSONL 语义 | 内容类型 | Hook 目标 | 关联等级 |
| --- | --- | --- | --- |
| 用户可见消息 | `USER_INPUT` | 同 `turn_id` 的 Turn | `BOUNDED` |
| 模型可见消息 | `MODEL_OUTPUT` | 同 `turn_id` 的 Turn | `BOUNDED` |
| `task_complete.last_agent_message` | `MODEL_OUTPUT` | 同 `turn_id` 的 Turn | `BOUNDED` |
| 可见 reasoning summary | `REASONING_SUMMARY` | 同 `turn_id` 的 Turn | `BOUNDED` |
| function/tool call | `TOOL_INPUT` | 同 Session/Turn 且公共 ID 已验证相等的 Tool | `EXACT`，否则 Turn `BOUNDED` |
| function/tool output | `TOOL_OUTPUT` | 同 Session/Turn 且公共 ID 已验证相等的 Tool | `EXACT`，否则 Turn `BOUNDED` |

同一已校验 transcript 内，工具调用与工具结果先按完全相同的 JSONL `call_id` 分组。若组内恰好只有一个 `turn_id` 对应现有 Hook Turn，则该 Turn 是整组工具内容的唯一边界，即使另一条记录携带不同的 JSONL 内部 `turn_id`，也只按 `BOUNDED` 补到该 Turn；仅当 `call_id` 还与该 Hook Turn 的 `tool_use_id` 相等时才是 `EXACT`。零个或多个 Hook Turn 候选时不挂接，不使用时间接近消除歧义。

正文只提取用户可见字符串及结构化工具参数；原始 JSONL 仍作为单独证据返回。没有 `turn_id` 的普通内容不猜测归属，不挂接到最后一个 Turn。

## 测试用例

| 编号 | 层级 | 场景 | 预期 |
| --- | --- | --- | --- |
| UT-TC-001 | Domain | 空、缺失、越界、符号链接、不可读、有效路径 | 产生明确路径状态，非有效路径不读取正文 |
| UT-TC-002 | Domain | session_meta 缺失、不支持、ID 不一致、匹配 | 只有匹配时允许补齐 |
| UT-TC-003 | Adapter | 版本化合成 JSONL | 解析用户、模型、`task_complete` 最终输出、reasoning、工具输入/输出及原始证据 |
| UT-TC-004 | Application | 匹配与错配绑定 | 匹配时幂等保存补齐；错配时零补齐 |
| UT-TC-005 | Application | call_id 相等、不等、缺失或多个候选 | 只在已验证同值契约下标记 `EXACT`，其他保持 `BOUNDED` 或不挂接 |
| UT-TC-006 | Domain | 同 call_id 的工具输入/输出携带不同内部 turn_id | 唯一现有 Hook Turn 时整组 `BOUNDED` 补齐；零个或多个候选时不挂接 |
| IT-TC-001 | MySQL | Hook 骨架加匹配 transcript | 绑定、补齐、查询 API 在同一业务语义下可见且重复执行不重复 |
| IT-TC-002 | MySQL | session ID 错配与越界路径 | 不写 `jsonl_supplement`，状态返回明确失败原因 |
| UT-TC-FE-001 | 前端 | Trace 响应含四类内容 | Hook 节点显示 JSONL 输入/输出和原始 JSONL，缺失 OTel 仍诚实降级 |
| IT-HOOK-TRACE-002 | 人工集成 | 真实 Codex Hook 与 transcript | 按 `07-main-flow-acceptance.md` 完成主链路与代码阅读 |

## 代码阅读路线

人工验收第 12 步按以下顺序检查，重点确认业务边界和证据来源，而不是逐行审阅框架样板：

1. 从 `interfaces/transcriptcontent/TranscriptContentScheduler` 开始，确认调度入口只触发补齐用例，不承担文件解析、关联或持久化规则。
2. 阅读 `application/transcriptcontent/SupplementTranscriptContentUseCase`，确认它只编排路径解析、记录解析、领域服务和多个 Repository，并由应用层定义事务边界。
3. 阅读 `domain/transcriptcontent/TranscriptBinding` 与 `TranscriptContentService`，对照“绑定不变量”和“已知内容契约”，确认只有 `VALID/MATCHED` 才补齐，且工具关联不会因时间接近被提升为 `EXACT`。
4. 阅读 `domain/transcriptcontent` 下各 `*Repository`，确认绑定、原始未知证据、JSONL 补齐和 Hook 目标分属明确持久化边界。
5. 阅读 `infrastructure/transcriptcontent/FileSystemTranscriptSourceGateway` 与 `JacksonTranscriptRecordParser`，确认路径越界/符号链接检查和版本化 JSONL 解析位于适配器层。
6. 阅读 `infrastructure/transcriptcontent` 下 MyBatis Repository、`TranscriptContentMapper` 和 `TranscriptContentMapper.xml`，确认 Mapper 只执行简单查询与写入，不承载状态迁移、关联等级或证据构建规则。
7. 从 `interfaces/transcriptcontent/TranscriptStatusController` 进入 `GetTranscriptStatusUseCase`，确认状态接口返回结构化 DTO，且日志不输出路径、正文或原始 JSON。
8. 从 `interfaces/tracequery/TraceAnalysisController` 进入 `GetTraceAnalysisUseCase`、`TraceAnalysisRepository` 和 MyBatis 适配器，确认 Trace 查询通过独立用例返回 Hook、JSONL 和 OTel 各自证据，不把 JSONL 当作骨架来源。
9. 阅读 `frontend/src/data/traceMapper.ts`、`TracePage.vue` 和 `EventInspectorDrawer.vue`，确认正式页面显示内容类型、来源、适配器版本与关联等级，并对缺失 JSONL/OTel 诚实降级。

## 自动测试证据

- 后端：JDK 17 + 独立 MySQL 测试库执行 `clean verify`，49 项通过，0 失败、0 错误。
- 前端：5 个测试文件、19 项测试通过。
- 前端生产构建：通过；现有 bundle 大小提示不阻塞 S2。
- 自动测试不替代 `IT-HOOK-TRACE-002` 的真实 Codex 主链路与用户代码阅读。
- 人工验收：`IT-HOOK-TRACE-002` 步骤 1–12 和代码阅读已通过；用户于 2026-09-16 明确接受 S2。

## 一致性门禁

| 检查面 | 结论 | 处理 |
| --- | --- | --- |
| PRD 与 V2 | Hook 建骨架、JSONL 补内容、OTel 补性能一致 | 不修改冻结原型，不创建 V3 |
| 数据关联规则 | session_meta 一致后才挂接；call_id 命名空间不默认相同 | 适配器版本与证据显式返回 |
| 当前实现 | `TranscriptWorker` 混合调度、文件、解析、关联和 Mapper；REST/持久化使用通用 Map | 本 Story 按四层和独立 Repository 重构 |
| 测试独立性 | 单元测试不得读取真实 Codex 目录或用户数据库 | 使用临时目录、合成 fixture 和独立 MySQL 测试库 |

不存在需要变更 PRD 或创建 V3 的阻塞项。

## Definition of Done

- S2 范围内的领域化重构与正式前端接入完成。
- 单元测试、JDK 17/MySQL 集成回归和前端测试通过。
- `IT-HOOK-TRACE-002` 由用户按明确步骤执行并通过。
- 用户完成代码阅读并明确接受 S2。
- 无真实 transcript、账号、路径、日志或凭据进入仓库。
- 未经用户明确指令，不启动 S3/S3.1。
