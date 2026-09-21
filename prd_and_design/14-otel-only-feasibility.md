# E1-S2.2 OTel + Transcript 数据源方案升级 Story 规格

## Story 信息

- 编号：E1-S2.2。
- 名称：OTel-first 数据源方案升级。
- 类型：需求与总体技术方案升级，不是当前 Hook-first 实现的缺陷修复。
- 状态：开发中；用户于 2026-09-18 确认 V1.0 采用 OTel + Transcript，当前按可行性先行规则验证双源能力覆盖，尚未进入 V3 详细产品方案。
- 启动原因：用户于 2026-09-17 提出 OTel 可以作为行为与性能基础并直接关联 transcript，Hook 可能不再是必要输入；既有 S2.1-S2 因此前提变化暂停人工验收。
- 前置证据：S2.2-S1 首轮 Codex 0.154.0 采样已经完成。
- 后续门禁：完成 PRD、技术设计、数据关联、数据库、验收用例和 V3 原型规格的一致性评审前，不删除 Hook、不改写业务 schema、不启动代码迁移。

## 目的与状态

- 目的：验证 Codex OTel 能否作为 Session、Turn、Tool Call 行为与性能主来源，并直接关联 TranscriptItem，从而移除必需的 Hook 链路。
- 版本：`codex-cli 0.154.0`，采样日期 2026-09-17 至 2026-09-21。
- 状态：Session/Turn、本地工具和 Turn 终态的版本化证据已取得；用户已确认 hosted tool 级生命周期移出 V1.0，并登记为 V1.1 TODO，也已确认本地命令结果由 Transcript 拥有；只剩并行子执行模型待确认。
- 安全：真实 OTLP 和 transcript 只在本机被忽略目录中处理；本文不记录真实 ID、正文、路径、凭据或错误原文。

## 业务问题

现有 Hook-first 方案让 Hook 建立 Session、Turn 和 Tool Call 行为骨架，transcript 补内容，OTel 补性能。新的样本证据表明 OTel 已携带与 transcript 相同的 Session/Turn 身份。如果 OTel 也能稳定覆盖工具行为和终态，继续维护 Hook 接收、forwarder、Hook 归一化、Hook/Transcript/OTel 三方关联会产生不必要的安装、配置、数据重复和关联复杂度。

本 Story 要回答的不是“OTLP 格式是什么”，而是 Codex 这个生产者在受支持版本中实际提供哪些业务事实，以及这些事实是否足以让产品采用 OTel-first：

- Session、Turn 和 Tool Call 的业务身份是否完整且稳定。
- 成功、失败、中断、重试和缺失终态是否可表达。
- OTel 与 transcript 是否能通过共同身份或可解释的时间/类型/顺序证据关联。
- exporter 未启用或发送失败时，产品如何诚实降级；最终决策是不引入 Hook 兜底，只保留 Transcript 检查能力。

## Story 目标

- 用公开 OTLP schema 与版本化 Codex 样本共同建立生产者契约，不从字段名称猜测语义。
- 确定 Execution、Transcript、Trace 的问题域职责及数据所有权，并明确现有 Hook 只属于迁移历史实现。
- 定义 Session、Turn、Tool Call 三级关联规则、证据等级、歧义处理和重算条件。
- 比较并选择 Hook + OTel + Transcript、OTel + Transcript、Hook + Transcript 三种融合方案。
- 在写业务代码前同步修订 PRD、技术设计、数据关联模型、Epic 台账、schema baseline 3 和集成测试计划。
- 明确 S2.1-S2 已完成代码的复用、迁移或删除策略，避免在未确认方案上继续投资。

## 非目标

- 本 Story 不直接删除 Hook、forwarder 或当前 Execution 代码。
- 不以一次失败采样证明所有 Codex 版本的长期契约。
- 不把时间接近自动提升为 `EXACT`，也不要求不同来源的 Call ID 名称或值必须相同。
- 不修改冻结的 V1/V2 原型；若最终方案改变用户可见的来源状态或降级交互，先更新 PRD 并创建新原型版本。
- 不提前实现完整 S3 性能分析、SSE 或 E2 诊断能力。

## 契约依据

OTLP 的 envelope、LogRecord、Span、Metric、resource、attribute、时间戳和 Trace/Span ID 由 OpenTelemetry protobuf 协议定义。Codex 具体发送哪些事件和属性属于生产者契约，不能由 OTLP 标准本身推导。

OpenAI 官方在线文档已可通过 Playwright 打开并核验；公共合同仍以官方 Advanced Configuration 的 OTel 章节为依据。本机 Codex 0.154.0 的配置模式和实际进程共同验证了以下能力：

- 独立的 log、trace、metric OTLP/HTTP exporter。
- HTTP binary protobuf 载荷。
- `conversation.id`、`turn.id`、`event.name` 等业务属性。
- `codex.startup_phase`、`codex.websocket_connect`、`codex.api_request`、`codex.auth_recovery` 等实际事件。

因此，本文把公开 OTLP schema 视为传输契约，把 Codex 0.154.0 样本视为版本化适配证据，不把本机二进制字符串声明为长期公共协议。

## 采样方法

1. 独立接收器监听 `127.0.0.1:4318` 的 `/v1/logs`、`/v1/traces` 和 `/v1/metrics`。
2. Codex 通过单次命令行覆盖启用三个 OTLP/HTTP binary exporter，不修改用户级配置。
3. 接收器保存原始 protobuf、内容摘要、白名单请求头和 `protoc --decode_raw` 结果。
4. 使用 OpenTelemetry proto v1.8.0 对目标批次再次结构化解码。
5. 只在内存/临时文件中比较 OTel 身份与对应 transcript 身份；报告只记录相等性结论。

首轮及 2026-09-18 认证失败复采共形成 132 个批次。2026-09-21 使用可成功请求的目标 provider 重新采集普通回复、本地工具成功/失败、串行、并行、重试、用户中断、异常进程结束和 hosted web search；最新样本目录共 237 个批次，其中 logs 156、traces 65、metrics 16。提交到仓库的安全契约只保留事件/指标名称与属性键，原始 OTLP 和 Transcript 继续留在 Git 忽略目录。

## 已确认结果

| 检查项 | 结果 | 架构含义 |
| --- | --- | --- |
| OTel `conversation.id` 与 transcript Session ID | 完全相等 | Session 级关联不需要 Hook，也不需要时间推断 |
| OTel `turn.id` 与 transcript Turn ID | 完全相等 | Turn 级关联可使用 `(conversation.id, turn.id)` 精确完成 |
| 事件自身时间 | LogRecord 与 Span 均存在 | 可用于排序、耗时和缺失共同调用 ID 时的候选窗口 |
| logs/traces/metrics 三类信号 | 均实际到达 | logs 适合业务事件，traces 适合耗时，metrics 只适合聚合趋势 |
| 失败与传输降级 | 存在明确事件 | OTel 能覆盖至少一类非成功生命周期，不只包含成功性能 Span |
| 本地工具调用身份 | `codex.tool_result.call_id` 与 Transcript 模型 `custom_tool_call.call_id` 同值 | Codex 0.154.0 可建立 Tool `EXACT`；子 `CommandExecution.id` 是另一层身份 |
| 本地工具结果 | OTel `success` 与 shell 退出成功不等价 | 用户已确认命令状态读取 Transcript `CommandExecution.status/exit_code`，缺失时为 `UNKNOWN` |
| Turn 终态 | 成功、API 失败、用户中断、异常退出均有样本 | 可区分 `SUCCEEDED/FAILED/INTERRUPTED/INCOMPLETE`，缺失证据不合成成功 |
| Hosted tools | web search 可执行，但无单次调用身份、状态或耗时证据 | 用户已确认从 V1.0 移除，并登记为 V1.1 TODO |

`thread.id` 在 OTLP Span 中是运行时线程的数值属性，不是 Codex Session 身份；不得与 transcript Session ID 混用。Codex 会话身份使用 `conversation.id`。

## 融合方案决策

| 方案 | 能力覆盖 | 优点 | 不选择的原因 |
| --- | --- | --- | --- |
| A Hook + OTel + Transcript | Hook 行为边界、OTel 性能、Transcript 内容 | 最大化保留已实现能力，单一来源暂时缺失时可能多一份证据 | 三套采集、两组跨源关联和冲突仲裁长期存在；Hook 无官方事件时间、不重放历史且不覆盖 hosted tools，新增证据不能弥补 OTel 缺失时的 API/TTFT 能力 |
| B OTel + Transcript | OTel 行为与性能、Transcript 可见内容与原始记录 | 两个来源通过 Session/Turn 公共身份精确汇合；0.154.0 的本地工具模型 Call ID 也可精确关联；同时覆盖核心性能目标、历史内容和原始证据 | hosted tools 缺少单次生命周期；本地命令结果和并行子执行语义仍需显式裁决 |
| C Hook + Transcript | Hook 行为边界、Transcript 内容和时间戳估算 | 不依赖 exporter，现有实现和人工验收基础较多 | 无法提供可靠 API、TTFT、传输、父子 Span 和精确性能链，不能满足产品核心目标；Hook 对 hosted tools 和历史执行仍有缺口 |

**最终选择 B：V1.0 融合 OTel + Transcript。** 用户已于 2026-09-18 确认该选择。Hook 不进入 V1.0 目标运行架构，也不作为可选兜底来源。现有 Hook、forwarder 和 Execution 实现仅在迁移完成前作为可回归、可回滚的历史实现保留，不能继续向目标模型写入第二套 Session/Turn/Tool 事实。

选择依据：

1. OTel 是 API、TTFT、传输回退、父子调用链和精确耗时的唯一来源；去掉 OTel 会直接失去产品核心价值。
2. Transcript 是用户可见输入输出、工具参数/结果、历史补扫和原始证据的唯一完整来源；OTel 不能替代正文。
3. 两者已经通过 `conversation.id/session_id` 与 `turn.id` 的版本化样本证明 Session/Turn 可精确关联，无需 Hook 中转。
4. Hook 的无事件时间、无历史重放、覆盖不完整和额外信任/forwarder 运维，使其不适合作为主源或长期兜底；保留 Hook 只会引入重复事实、冲突优先级和额外完整度口径。
5. Codex 0.154.0 的本地工具模型 Call ID 已验证可精确合并；其他版本或 hosted tools 没有共同身份时，性能节点与内容节点仍保持分离，符合产品“不伪造精确调用链”的原则。

## 主事实来源与冲突裁决

目标 Trace 以 OTel 为主：OTel 创建可分析的 Session/Turn 执行骨架，并拥有生命周期、状态、事件时间、Span 父子关系和性能字段。Transcript 不覆盖这些字段。

“以 OTel 为主”不表示 OTel 覆盖所有字段。事实所有权按业务语义固定：

| 事实 | 权威来源 | 冲突处理 |
| --- | --- | --- |
| Session/Turn 执行存在、生命周期、终态、事件时间 | OTel | Transcript 同身份内容可挂接；身份不一致时不合并，不用 Transcript 改写 OTel |
| API、传输、重试、Span 父子关系及经版本验证的 TTFT/精确耗时 | OTel | Transcript 时间戳只能作为内容发生时间或降级估算，不能覆盖 OTel；匿名目录本身不是本地 OTel 证据，实际 OTLP 样本可形成版本化证据 |
| 用户输入、模型可见输出、reasoning summary、工具参数/结果、原始 JSONL | Transcript | OTel 摘要不得覆盖 Transcript 原文 |
| Tool Call 内容身份 | Transcript `(sessionId, turnId, callId)` | 0.154.0 的模型 Call ID 已验证可 `EXACT`；并行 `CommandExecution` 的候选方案是保留为该模型调用的子项，仍待用户确认 |
| 本地命令执行结果 | Transcript `CommandExecution.status/exit_code` | 用户已确认 OTel `tool_result.success` 不得覆盖命令失败；Transcript 缺失时为 `UNKNOWN` |
| Session/Turn 跨源身份 | OTel 与 Transcript 共同校验 | 值相等才是 `EXACT`；不存在“某一侧覆盖另一侧”的修复 |

因此，对“以谁为准”的最终回答是：**执行与性能主链路以 OTel 为准；内容事实以 Transcript 为准；跨源身份必须双方一致，不设静默覆盖；Hook 不参与最终裁决。**

### V1.0 能力承诺

V1.0 以产品分析语义为验收对象，不承诺把 Hook API 逐事件、逐字段无损复刻：

- OTel 提供官方已定义的核心行为遥测与性能事实，包括 conversation、user prompt、模型 API/流事件、工具 decision/result 及相应 metrics；具体可用字段仍由版本化 Codex 适配器锁定。
- Transcript 提供用户/模型可见内容、工具参数与结果、任务完成、compaction、hosted tools 等已解析记录，并作为 OTel 缺少正文时的内容权威来源。
- Hook 特有但无法从两源取得等价证据的回调边界，不得用时间或结果反推为已发生；V1.0 显示为“不支持/无证据”，并记录在覆盖矩阵。
- OTel metrics 只用于聚合趋势，不能反推单次 Hook、工具或模型事件；单次 Trace 必须使用 OTel logs/traces 与 TranscriptItem。
- 官方页面中默认回传 OpenAI 的匿名 analytics `Metrics` catalog 与 OTel export 独立，目录名称本身不得计入本地 OTLP 覆盖；目标版本真实 OTLP 样本中实际出现的同名指标可计为 `VERSIONED_SAMPLE`，但只能用于聚合趋势。
- V1.0 的正式 Trace 要求存在可识别的 OTel Session/Turn；只有 Transcript 时保留来源健康、原始记录与内容检查，不伪造性能 Trace。

## 对关联方案的修正

跨源 ID 不要求名称相同，也不预设 OTel 工具调用 ID 与 Transcript `call_id` 相等。关联按证据分层：

1. Session：OTel `conversation.id == TranscriptMeta.sessionId`，可作为版本化适配器下的精确证据。
2. Turn：同一 Session 下 OTel `turn.id == TranscriptItem.turnId`，可作为精确证据。
3. Tool Call：Codex 0.154.0 的 OTel `call_id` 与 Transcript 模型级 `custom_tool_call.call_id` 已经样本验证，可精确关联；没有共同身份的其他版本或工具类型才在精确 Session/Turn 内形成 `INFERRED` 候选。
4. 多个候选时间重叠或顺序无法消歧时保持 `UNMATCHED`，不能选择最近记录冒充精确关系。

所以“Call ID 不相等”本身不是问题。工具级候选在并行、重试和缺失事件场景下不能唯一收敛时，性能节点与内容节点保持分离。产品接受诚实的 `INFERRED/UNMATCHED`，但不接受把它升级为 `EXACT`。

## 未完成交付门禁

完整覆盖矩阵、验证方法与通过标准见 [V1.0 OTel + Transcript 来源覆盖可行性](./15-v1-source-coverage-feasibility.md)。F-01、F-02、F-04、F-05 已通过；F-03 已证明当前版本不满足 hosted tool 级生命周期能力。2026-09-21 用户已确认以下两项：

- V1.0 移除 hosted tool 级身份、状态和耗时，只保留 Turn 级影响及最终可见内容；完整生命周期登记为 V1.1 TODO。
- OTel 拥有本地工具发生、事件时间、耗时和 trace context；Transcript `CommandExecution.status/exit_code` 拥有命令执行结果，缺失时为 `UNKNOWN`。

仍待确认：一个模型 `custom_tool_call` 包含多个 `CommandExecution` 时，是否建模为一个模型 Tool Call 及其子执行，不扁平化或重复计算耗时。
- 在代码迁移前完成 PRD、技术设计、数据关联、数据库与验收用例修订，并建立反映 OTel + Transcript 来源状态的 V3 原型规格。
- Tool `EXACT` 只适用于版本化样本已验证的本地工具模型 Call ID；不得外推到 hosted tools、子 `CommandExecution.id` 或其他 Codex 版本。

## 子 Story 与交付顺序

### S2.2-S1 Session/Turn 可行性采样（已完成）

- 实现独立、仅回环监听且不连接业务数据库的 OTLP/HTTP 采样器。
- 捕获并按官方 OpenTelemetry proto 解码 Codex logs、traces、metrics。
- 比较同一失败会话的 OTel 与 transcript 身份。
- 结论：`conversation.id` 和 `turn.id` 分别与 transcript Session/Turn 同值，Session/Turn 精确关联不需要 Hook。

### S2.2-S2 工具级与终态采样（已完成）

- 已采集普通回复、工具成功、工具失败、中断、串行、并行、重试和异常退出。
- 已验证 Tool 事件/Span 的身份、名称、时间、输入输出摘要、结果冲突和重试语义。
- 已验证模型级公共 Call ID；并行场景的多个命令是单个模型调用的子执行。
- 已更新安全字段契约；真实样本不进入仓库。

### S2.2-S3 方案决策与规格升级（开发中，方案已确认）

- 已评审三种融合方案，用户于 2026-09-18 确认 V1.0 选择 OTel + Transcript、OTel-first；Hook 不进入目标架构。
- 更新 `01-product-requirements.md`、`02-technical-design.md`、`03-data-and-correlation.md`、数据库迁移说明和主链路验收用例。
- 明确问题域、实体、聚合、Repository、Query/Change Feed、事务和降级规则。
- 若用户可见语义或交互改变，创建新原型版本；不得覆盖冻结 V2。
- 总体来源方案选择门禁已满足；完成剩余的并行子执行语义裁决、一致性评审和 V3 原型确认后，才进入代码 Story。

### S2.2-S4 代码迁移与回归（待启动）

- 仅按 S2.2-S3 确认的方案拆分新的代码 Story，不在本规格阶段预设删除范围。
- 先写目标接口、聚合和关联策略测试，再迁移 OTel、Transcript 与 Trace；Hook 只按明确迁移步骤停止写入并最终移除。
- 使用新空测试 schema 验证幂等、乱序、事务、并发、变更游标和重算。
- 回归已验收的 S1、S1.1、S2 主流程，并由用户完成代码阅读与人工验收。

## 验收标准

- Session/Turn/Tool Call 三层事实均有版本化样本证据、字段路径和唯一性范围。
- 工具成功、失败、中断、重试、并行和缺失终态均有明确行为，不以单一成功样本代替。
- OTel-to-Transcript 关联规则明确区分 `EXACT`、`BOUNDED`、`INFERRED`、`UNMATCHED`，并保存算法版本和证据。
- Call ID 不同不会阻止关联；时间/类型/顺序也不会在存在多个候选时被误报为精确关联。
- exporter 未配置、OTLP 投递失败和 transcript 缺失分别有产品降级与状态展示规则。
- PRD、技术设计、数据关联、数据库和集成测试用例对最终方案一致。
- 用户明确确认方案选择；S2.1-S2 的复用、改造或废弃策略在代码迁移 Story 启动前落实到文件和表级清单。

## 测试矩阵

| 用例 | 输入场景 | 预期结论 |
| --- | --- | --- |
| OTEL-TRANSCRIPT-001 | 普通回复，无工具 | Session/Turn 共同身份一致，消息可归属同一 Turn |
| OTEL-TRANSCRIPT-002 | 单个工具成功 | 确认工具开始、结束、类型、结果及与 transcript Call 的证据等级 |
| OTEL-TRANSCRIPT-003 | 工具失败 | OTel 保留调用结果与时间，Transcript 命令状态保留失败事实，Turn 不被误标成功 |
| OTEL-TRANSCRIPT-004 | 工具中断或无终态 | 保留不完整生命周期并明确降级，不合成成功终态 |
| OTEL-TRANSCRIPT-005 | 同 Turn 同类工具串行 | 共同 ID 优先；无 ID 时顺序与时间候选可唯一解释 |
| OTEL-TRANSCRIPT-006 | 单个模型 Tool Call 内多个命令并行 | 模型 Call ID 精确关联；待确认候选预期为命令保留为子执行，不扁平化或重复累计耗时 |
| OTEL-TRANSCRIPT-007 | OTLP 重复、乱序、延迟 | 幂等保存并通过 Change Feed 重算后收敛 |
| OTEL-TRANSCRIPT-008 | exporter 未配置或投递失败 | 状态显示覆盖未知/缺失，不把 transcript 估算冒充 OTel |

## 当前结论

S2.2 是当前优先 Story。用户已于 2026-09-18 确认 V1.0 采用 OTel + Transcript、OTel-first：OTel 拥有核心行为遥测与性能事实，Transcript 拥有可见内容，Hook 不进入目标架构；V1.0 不承诺逐事件复刻 Hook。2026-09-21 已完成 S2.2-S2，本地 Tool `EXACT` 和 Turn 四类终态获得版本化样本证据；hosted web search 则证明当前版本不能提供可交付的 tool 级生命周期。用户已确认 hosted tool 能力移至 V1.1 TODO，并确认本地命令状态所有权；S2.2 只剩并行子执行模型待确认，确认前不进入 V3，也不删除 Hook 历史实现。
