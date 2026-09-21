# V1.0 OTel + Transcript 来源覆盖可行性

## 文档目的

本文是 V1.0 进入详细产品方案和 V3 原型前的可行性门禁。用户已于 2026-09-18 确认数据源选择为 OTel + Transcript；本门禁只验证这两个来源能否覆盖 V1.0 所需的产品分析语义，不重新比较或引入 Hook，也不要求逐事件复刻 Hook API。

## 当前状态

- 阶段：需求澄清，关键技术可行性验证中。
- 已通过：F-01 官方 OTel 合同已锁定；F-02 已用 Codex 0.154.0 验证本地工具成功、失败、串行、并行、重试和中断；F-04 已验证普通完成、API 失败、用户中断和进程异常结束；F-05 的官方配置边界、接收状态和双源组合降级最小实验已通过。2026-09-21 最新采样共 237 个 OTLP 批次，事件/指标名称与属性键已形成不含值的版本化样本契约。
- 已确认裁决：hosted tool 级身份、状态和耗时移出 V1.0，并登记为 V1.1 TODO；本地命令结果由 Transcript `CommandExecution.status/exit_code` 拥有，缺失时为 `UNKNOWN`。
- 待用户决策：一个模型 Tool Call 内包含多个并行 `CommandExecution` 时的父子建模仍需人工门禁确认。
- 门禁：P0 能力全部达到“已验证覆盖”或由用户明确从 V1.0 移除后，才允许开始 V3 详细产品方案。

## 证据等级

| 等级 | 定义 | 可支持的结论 |
| --- | --- | --- |
| `OFFICIAL` | OpenAI 官方文档明确列出事件/指标及字段语义 | 可作为公共契约，但仍需目标 Codex 版本适配测试 |
| `VERSIONED_SAMPLE` | 目标 `codex-cli 0.154.0` 的脱敏真实 OTLP/Transcript 样本 | 可锁定当前版本适配器和集成测试 |
| `SYNTHETIC_FIXTURE` | 仓库中的虚构 fixture 或自动测试 | 可验证本项目解析和降级规则，不能证明 Codex 实际发送 |
| `ASSUMED` | 仅由名称、二进制字符串或相邻结果推断 | 不得进入正式能力或 `EXACT` 关联 |

官方参考入口为 [Codex advanced configuration: OTel events and metrics](https://learn.chatgpt.com/docs/config-file/config-advanced#otel-metrics-emitted)。用户于 2026-09-18 提供该页面的 `What gets emitted`、`OTel metrics emitted` 和 `Metrics` 正文，仓库据此形成只含公共名称和字段的官方合同快照。页面把 structured log event 称为 representative event types，因此未列出的事件仍不得推定存在。

页面中的两套 metrics 必须严格区分：`OTel metrics emitted` 表是官方明确的公共 OTel 契约；后续 `Metrics` catalog 是 Codex 默认发送给 OpenAI 的匿名使用与健康数据，并明确与 OTel log/trace export 相互独立。匿名目录本身不能证明本产品 OTLP 接收端可获得同名指标，但目标版本真实 OTLP 样本可以形成 `VERSIONED_SAMPLE` 证据。2026-09-21 样本实际收到 `codex.turn.ttft.duration_ms`、`codex.turn.e2e_duration_ms`、`codex.turn.tool.call`、`codex.tool.call`、`codex.hooks.run` 等指标；这些仍是聚合观察，不得用于重建某个具体 Turn 或工具调用。

## V1.0 能力覆盖矩阵

| 优先级 | 产品能力 | OTel 候选证据 | Transcript 候选证据 | 当前结论 | 剩余门禁 |
| --- | --- | --- | --- | --- | --- |
| P0 | Session 身份与开始 | 官方 `codex.conversation_starts`；样本 `conversation.id` | `session_meta.payload.session_id` | `OFFICIAL + VERSIONED_SAMPLE`，可精确关联 | Codex 升级时回归版本合同 |
| P0 | Turn 身份与用户提交 | 样本 `turn.id`；官方 `codex.user_prompt`，正文默认脱敏 | `user_message.turn_id` 与正文 | `OFFICIAL + VERSIONED_SAMPLE`，身份可精确关联，正文只以 Transcript 为准 | 不依赖 OTel prompt 正文开关 |
| P0 | 模型请求、流与性能 | 官方 API、SSE、WebSocket logs；样本含成功流、Token、`codex.turn_ttft` 和聚合 TTFT/E2E metrics | 模型可见消息与 reasoning summary | `OFFICIAL + VERSIONED_SAMPLE`，OTel 为性能权威 | 单次 TTFT 只使用带 Turn trace context 的 log/trace；Metric 只用于聚合 |
| P0 | Turn 成功完成与最终输出 | 成功响应、失败请求、取消/中断和无终态样本 | `task_complete.last_agent_message`、`turn_aborted` 或终态缺失 | F-04 通过，可区分 `SUCCEEDED/FAILED/INTERRUPTED/INCOMPLETE` | 规则进入后续正式实现测试 |
| P0 | 本地工具调用与结果 | `codex.tool_decision`、`codex.tool_result`、Call ID、时间和聚合 tool metrics | `custom_tool_call.call_id`；`CommandExecution.status/exit_code` | F-02 通过；模型 Call ID 可 `EXACT`；命令结果由 Transcript 拥有 | 用户确认并行子执行模型 |
| 后续版本 | Hosted tools | hosted web search 可执行，但未观察到可识别的单次搜索 OTel 生命周期 | 对应 Transcript 未出现 `web_search_call` | 已从 V1.0 移除 tool 级身份、状态和耗时 | V1.1 TODO：重新验证目标版本生产者契约 |
| P0 | exporter 缺失或投递失败 | 接收端只能知道未收到或失败，不能证明发送前丢失 | Transcript 仍可独立发现和读取 | `OFFICIAL + SYNTHETIC_FIXTURE`；配置、接收状态与双源组合降级已验证 | 正式迁移 Story 仍须实现并回归；零请求不得显示为成功 |
| P1 | 工具审批与等待 | 官方 `codex.tool_decision` 表达批准/拒绝及配置/用户来源；等待起点、持续时间和调用身份未声明 | 工具调用内容可能存在，不保证审批边界 | 决策语义为 `OFFICIAL`，等待耗时未验证 | 验证允许、拒绝、超时和等待时长字段；无证据则 V1.0 显示未知 |
| P1 | Compaction | 暂无已验证专用 OTel 事件 | Transcript 可能保存 compaction 记录 | 仅产品候选 | 增加版本化 Transcript fixture；只展示记录，不伪造 Pre/Post Hook 边界 |
| P1 | 中断与缺失终态 | 已观察取消/中断属性、API 失败和异常退出无终态 | `turn_aborted` 或 `task_complete` 缺失 | `VERSIONED_SAMPLE`，F-04 通过 | 后续实现不得把缺失终态合成为成功 |
| P2 | Subagent 生命周期 | 暂无已验证 OTel 事件 | 暂无版本化 Transcript 契约 | 不支持 | 若无官方/样本证据，从 V1.0 能力范围移除并在页面说明 |
| P2 | SessionEnd 回调等价事实 | 暂无已验证等价事件 | 文件停止增长不能证明 SessionEnd | 不支持 | V1.0 不展示显式 SessionEnd，除非后续取得直接证据 |

## 关键技术卡点

### F-01 官方 OTel 契约锁定

- 问题：必须区分官方 OTel events/metrics、目标版本实际载荷和与 OTel 独立的匿名 analytics catalog，避免把聚合指标或非导出指标误建模成单次事件。
- 验证：逐项记录官方事件/指标名称、字段、单位、可空性和内容开关；与 0.154.0 实际载荷比较。
- 通过标准：每个 P0 OTel 字段都有官方依据或明确标为仅版本样本；Metric 不参与单次事件重建。

#### F-01 阶段验证结果（2026-09-18）

| 证据 | 状态 | 结论 |
| --- | --- | --- |
| 官方 OTel 页面 | 完成 | 用户提供官方页面正文；8 类 representative structured log events、10 项 OTel metrics、默认 metadata tags 和用户提示默认脱敏规则已锁定到 `backend/src/test/resources/otel/codex-official-otel-contract-2026-09-18.json` |
| Codex 0.154.0 OTLP 样本 | 完成 | 2026-09-21 最新采样共 237 个批次：logs 156、traces 65、metrics 16；安全契约见 `backend/src/test/resources/otel/codex-0.154.0-observed-contract.json`，只保存名称和属性键，不保存内容、路径、ID、模型、账户或机器值 |
| 官方 OTel 与 0.154.0 样本差异 | 已分类 | 已观察 `codex.sse_event`、`codex.tool_decision`、`codex.tool_result` 和 `codex.turn_ttft`；未观察名称仍不得推定不存在，适配器必须保留未知事件 |
| 独立匿名 analytics catalog | 已界定 | 目录本身不是 OTLP 证据；同名指标只有在目标版本真实 OTLP 样本出现后才获得 `VERSIONED_SAMPLE` 等级，且只进入聚合指标能力 |

当前已锁定的 `VERSIONED_SAMPLE` 范围包含 `codex.conversation_starts`、`codex.user_prompt`、`codex.api_request`、认证恢复、启动和 WebSocket 降级事件，以及 API 请求等指标。该清单证明 0.154.0 在本次场景中实际发送过这些名称与属性键，不证明未观察名称不存在，也不证明字段在后续版本稳定。官方文档中的事件列表又明确是 representative，而非穷举列表，因此适配器仍必须保留未知事件原始证据。

F-01 已通过。通过表示官方 OTel 边界、0.154.0 已观察合同以及匿名指标目录与实际 OTLP 样本的证据边界已经明确；不把未观察名称推定为不存在，也不把聚合 Metric 当成单次事件。

### F-02 本地工具覆盖与关联

- 问题：验证本地工具 decision/result、Span、状态、Call ID、串并行和重试语义。
- 验证：单工具成功、失败/中断、同类串行、同类并行、重试五类样本，与 Transcript `call_id` 比较。
- 通过标准：每类都能保留完整原始证据；公共 ID 未验证时不产生 `EXACT`；并行歧义保持 `UNMATCHED`。

#### F-02 阶段验证结果（2026-09-21，通过）

- 普通成功、单工具成功、shell `exit 7` 失败、两个串行调用、两个并行 shell 子执行、失败后重试成功和用户中断均已形成本机忽略样本。
- OTel `conversation.id`、trace context 中的 `turn.id` 分别与 Transcript Session/Turn ID 同值；模型级 `codex.tool_result.call_id` 与 Transcript `custom_tool_call.call_id` 同值，因此目标版本可建立 Tool `EXACT`。
- 串行与重试各产生两个模型 Call ID。并行场景则是一个模型 `custom_tool_call` 包含两个 `CommandExecution` 子项；若扁平化成两个同级模型 Tool Call，会改变生产者表达的调用次数，并可能重复计算父子耗时，因此推荐保留父子结构，待用户确认。
- shell `exit 7` 时 Transcript `CommandExecution.status=failed` 且 `exit_code=7`，对应 OTel `codex.tool_result.success=true`。因此 OTel `success` 只能解释为工具协议调用产出结果，不能解释为本地命令业务成功。
- 用户于 2026-09-21 确认：OTel 拥有发生、事件时间、耗时和 trace context；Transcript `CommandExecution.status/exit_code` 拥有本地命令结果。Transcript 缺失时结果为 `UNKNOWN`。

### F-03 Hosted tools 覆盖

- 问题：Hook 已知不覆盖部分 hosted tools，而双源方案尚未证明可识别其实际执行生命周期。
- 验证：选择一个受支持 hosted tool，采集成功和失败场景，比较 OTel 流事件与 Transcript response item。
- 通过标准：至少能在精确 Turn 下展示工具类型、状态、时间和可见结果；若无法取得开始/结束，则产品明确只展示内容记录，不宣称工具耗时。

#### F-03 阶段验证结果（2026-09-21，能力不满足）

- 在禁用浏览器/CUA 路由后，通过目标 provider 的 standalone web search 能力成功执行 hosted web search。
- 对应 Transcript 未出现 `web_search_call`，OTel 未出现可识别的单次搜索 SSE kind、调用身份或开始/结束生命周期，只能确认 Turn 正常完成。
- Playwright 另行打开搜索发现的具体 RFC 页面并验证 HTTP 200、最终 URL、标题与 H1；该结果只证明页面渲染成功，不补足 hosted tool 生命周期证据。
- 用户于 2026-09-21 确认从 V1.0 移除 hosted tool 级生命周期、状态和耗时，只保留精确 Turn 下的影响与最终可见内容；完整 hosted tool 生命周期登记为 V1.1 TODO，届时重新验证目标 Codex 版本契约。

### F-04 Turn 终态与异常分类

- 问题：认证失败样本不能代表正常完成、工具失败、用户中断和无终态。
- 验证：普通成功、模型请求失败、工具失败、用户中断和进程异常结束。
- 通过标准：状态规则可以区分 `SUCCEEDED/FAILED/INTERRUPTED/INCOMPLETE`，缺失证据不合成成功。

#### F-04 阶段验证结果（2026-09-21，通过）

- 普通成功同时观察到 OTel `response.completed` 与 Transcript `task_complete`。
- API 认证失败具有终止请求/错误证据；用户中断具有 Transcript `turn_aborted` 和 OTel abort 属性；进程异常结束具有 `task_started`，但没有 `task_complete` 或 `turn_aborted`。
- 固定分类：成功终止证据为 `SUCCEEDED`；终止错误且无后续恢复为 `FAILED`；明确 abort/cancel 为 `INTERRUPTED`；已开始但没有任何终态为 `INCOMPLETE`。任何缺失证据都不得合成成功。

### F-05 缺源降级

- 问题：OTel exporter 未配置、发送失败、接收失败和 Transcript 缺失是不同故障，不能用单一“采集失败”表示。
- 验证：分别关闭 exporter、拒绝 OTLP、延迟 Transcript、制造不匹配 Session ID。
- 通过标准：来源状态和 Trace 能力逐项降级，零请求为覆盖未知，任何降级都不伪造性能或内容。

#### F-05 最小实验结果（通过）

接收侧必须分开发布三个维度，不能由一个状态互相推导：

| 维度 | 可观察证据 | 状态与规则 |
| --- | --- | --- |
| 接收器 | 本机监听/请求处理是否可用 | `LISTENING` 或 `UNAVAILABLE` |
| 已到达请求投递 | 请求数、2xx、非 2xx | 零请求为 `IDLE`；全部成功为 `READY`；成功失败混合为 `DEGRADED`；有请求且全部失败为 `FAILED`；零请求的成功率必须为 `null` |
| 数据覆盖 | 目标窗口是否存在应有的业务记录 | 接收端只看到零请求时必须为 `UNKNOWN`；不得把 `IDLE`、监听成功或 100% 已到达请求成功率解释为覆盖完整 |

最小实验使用不连接业务数据库的 `backend/scripts/otel_sampler.py`，只记录三类信号的请求、成功、失败与最近成功时间，并通过只读 `/status` 返回上述状态。Python 3.11 自动测试通过 7 项，其中真实回环 HTTP 覆盖启动后零请求、合法 OTLP JSON、非法载荷、同一信号成功与失败混合，分别验证 `IDLE/READY/FAILED/DEGRADED`、零请求成功率 `null` 和覆盖始终为 `UNKNOWN`。2026-09-18 的 Codex 复采另证明三类 OTLP binary exporter 可向回环接收器投递 26 个批次，但业务请求认证失败，不能作为成功 Turn 或工具覆盖证据。

该实验只能证明接收侧观测边界。`exporter 未配置` 与 `exporter 已配置但在请求到达前失败` 对接收端都表现为零请求，因此两者不能由接收端自动区分；后续 V3 必须把配置检测/发送端诊断与接收状态并列展示。Transcript 延迟、缺失和 Session ID 不匹配继续由 Transcript 来源状态单独表达，不改变 OTel 接收统计。

用户于 2026-09-18 提供的官方 Advanced Configuration 正文进一步锁定配置侧边界：OTel 默认关闭；`exporter = "none"` 时本地记录但不发送；CLI `--config` 支持一次性覆盖；项目级 `.codex/config.toml` 中的 `otel` 会被忽略；exporter 异步批量并在正常退出时 flush。由此，配置检测必须读取有效的用户/CLI 配置层，不能把项目配置中的 `otel` 当作已启用，也不能在进程尚未正常 flush 时把暂时零请求立即判为失败。这些事实已写入官方合同 fixture，F-05 配置侧通过。

组合降级最小实验固定以下判定，不涉及正式数据库或页面实现：

| OTel Turn | Transcript 状态 | 正式 Trace | 性能 | 内容与关联 |
| --- | --- | --- | --- | --- |
| 缺失 | 已发现 | 不创建，只允许 Transcript 检查 | `UNAVAILABLE` | `INSPECTION_ONLY/UNMATCHED` |
| 存在 | 缺失 | 创建 | `AVAILABLE` | `MISSING/UNMATCHED` |
| 存在 | 延迟 | 创建 | `AVAILABLE` | `PENDING/UNMATCHED`，后到后重算 |
| 存在 | Session/Turn 同值 | 创建 | `AVAILABLE` | `AVAILABLE/EXACT` |
| 存在 | Session/Turn 冲突 | 创建 | `AVAILABLE` | `CONFLICT/UNMATCHED`，保留两侧证据但不合并 |

探针只验证上述已确认产品不变量：正式 Trace 的存在与性能不依赖 Transcript；Transcript 不得创建或改写 OTel Execution；身份冲突不得静默修复。它不实现目标架构代码，也不证明 Codex 生产者会发送尚未采到的事件。

组合降级探针 5 项通过；加上 OTLP 接收器与官方合同 8 项测试，F-05 共 13 项最小实验通过。F-05 可行性门禁完成，但正式迁移 Story 仍必须在目标 Application/Domain/API 上重新实现并执行集成测试，不能把本探针当作产品代码完成。

既有 JDK 17 合成领域测试 `TranscriptBindingTest` 与 `GetTraceAnalysisUseCaseTest` 共 2 项通过，证明 Transcript 路径缺失、Session ID 错配会禁止内容挂接，且缺少 OTel 时不会伪造性能状态。这些测试属于已验收 Hook-first 基线，只支持来源状态彼此独立的规则，不能替代目标架构中“已有 OTel Turn、Transcript 延迟或缺失”的组合 Trace 验证。

## 门禁结论

当前双源架构选择成立，F-01、F-02、F-04、F-05 已通过。F-03 已验证 Codex 0.154.0 不能提供可交付的 hosted tool 级生命周期；用户已确认将其移出 V1.0，并登记为 V1.1 TODO，同时确认由 Transcript 命令状态拥有本地执行结果、缺失时为 `UNKNOWN`。V1.0 整体可行性只剩并行子执行父子模型待确认；确认前项目继续停留在需求澄清，不进入 V3 详细产品方案或正式业务代码迁移。
