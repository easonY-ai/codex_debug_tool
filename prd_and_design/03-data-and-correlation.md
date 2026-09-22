# 数据与关联模型

## 基本原则

V1.0 只融合 OTel 与 Transcript 两类独立输入。OTel 建立官方已定义的核心行为遥测与性能骨架，Transcript 补齐可见内容和原始记录。两者已在 Codex 0.154.0 样本中证明共享 Session/Turn 业务身份，但没有保证共享每一个工具调用的事件级主键，也不保证逐事件复刻 Hook，因此产品不能把时间接近或最终结果当成缺失边界的确定证据。

OTel 是 Session/Turn 生命周期、状态、事件时间、Span 父子关系和性能字段的主事实来源。Transcript 独立发现和解析 JSONL；每个 TranscriptItem 保存来源 Path、Transcript Meta Session ID 以及自身 Turn/Call 候选，并拥有用户可见正文、工具参数/结果和原始记录。跨源身份必须双方一致，任一来源不得静默覆盖另一来源。Hook 不参与目标关联模型；现有 Hook 数据只作为迁移前历史证据保留。

## 数据所有权与发布契约

| 事实或关系 | 唯一所有者 | 可发布内容 | 禁止行为 |
| --- | --- | --- | --- |
| OTLP 原始对象、Session/Turn/Model Tool Call 执行事实和性能 Span | Execution | 按复合 Turn 身份查询的执行/性能事实、单调变更序列 | 根据 Transcript 猜测 OTel 生命周期或在本域决定跨源关联等级 |
| Transcript 文件、Meta、检查点、Item、UNKNOWN | Transcript | 按 Path/Session/Turn 候选查询的 Transcript 和 Item、单调变更序列 | 覆盖 OTel 执行状态、Span 或性能字段 |
| Transcript Evidence Link、Tool Alignment、Turn Trace | Trace | 面向 API 的 Trace 读模型 | 修改两种来源事实，或把读模型当成写聚合 |
| 采集状态和运行指标摘要 | Operations | 脱敏健康状态 | 成为原始事件或业务生命周期的第二所有者 |

跨上下文传递稳定身份、revision、来源类型和必要摘要，不传递聚合对象。每个上游在保存业务变化的同一事务追加 `changeSequence`；Trace 以至少一次方式消费，重复变化必须幂等，只有链接和 Turn Trace 读模型成功提交后才推进自己的消费检查点。到达时间、事件时间和数据库自增 ID 均不能冒充跨来源公共 ID。

Transcript 独立完成路径安全、文件身份、`session_meta` 和 Item 解析，不消费 Execution 声明。Trace 从 Execution 获得 OTel `conversation.id/turn.id`、事件/Span/Model Tool Call 身份与性能事实，从 Transcript 获得 Item 的 `path/sessionId/turnId/callId` 和 `CommandExecution` 子执行证据，并在自己的策略中建立 Evidence Link。Trace 不重新解释任一来源协议，Transcript 也不查询 Execution 表选择目标。

## 标识符命名空间

- 已验证的 Codex 0.154.0 OTel 使用 `conversation.id` 标识 Session、`turn.id` 标识 Turn；`thread.id` 是运行时线程数值，不是 Session 身份。协议中的 Trace/Span ID 只标识 OTel 调用链，不能直接当作 Transcript Call ID。
- 已验证的 JSONL 格式在文件第一条 `session_meta` 记录的 `$.payload.session_id` 保存会话 ID；后续普通事件行不保证重复该字段。样本常见 `turn_id` 位于 `$.payload.internal_chat_message_metadata_passthrough.turn_id`，工具调用与结果的内部 `turn_id` 可能不同，但同一次 JSONL 工具调用共享 `$.payload.call_id`。
- OTel 工具调用身份与 JSONL `call_id` 属于不同命名空间，不能仅因用途相似就声明相同。只有版本化采集样本验证值和语义稳定时才可作为 `EXACT` 证据，否则只能在精确 Session/Turn 内结合工具类型、事件时间和顺序形成候选。
- `(session_id, turn_id)` 共同唯一标识一次执行轮次；`turn_id` 只在所属 Session 内唯一。同一 `turn_id` 出现在不同 Session 是两个合法 Turn，所有查询、Change Feed 和关联证据都必须携带复合身份。

## OTel 执行事实关联

OTel Record 使用协议身份或 `(batchId, objectIndex)` 幂等保存，不使用到达顺序推导业务顺序。Session/Turn 以 `(conversation.id, turn.id)` 建立复合身份；LogRecord/Span 的事件自身时间用于排序，Trace/Span 父子关系用于性能调用链。重复、乱序或延迟记录触发 Trace 重算，不允许终态回退，也不从缺失终态合成成功。

Tool Record 保留其 OTel 原始身份、工具类型、开始/结束、状态和父子 Span。工具级公共调用 ID 未通过版本化样本验证前，只能作为 OTel 性能节点存在；不得直接创建与 Transcript `call_id` 精确等同的合并节点。

## Transcript 来源与 Meta

Transcript 以安全规范化后的 Path 为业务身份。路径必须位于显式配置根目录内且为可读、非符号链接的普通文件；文件替换或截断在相同 Path 下产生新 generation。Transcript 保存文件身份、读取检查点和 `TranscriptMeta`，但不包含全部 TranscriptItem 集合。

对当前已验证格式，首条完整记录必须为 `type=session_meta`，其 `$.payload.session_id` 与适配器版本组成 `TranscriptMeta` 值对象。该 `session_meta` Item 的 Session ID 来自自身解析结果；每个后续 TranscriptItem 在创建时复制 Transcript Path 与 Meta Session ID，保证离开聚合后仍能描述原始来源。普通事件行不要求重复 `session_id`。Meta 缺失或格式未知时仍保留原始 Item，但 Session ID 为空，不能与 OTel 建立正式内容关联。

Trace 的 Session 候选要求 OTel `conversation.id` 与 Transcript Meta Session ID 一致；Turn 候选还要求同一 Session 下 OTel `turn.id` 与 TranscriptItem Turn ID 一致。具体消息和工具仍逐条关联 TranscriptItem；身份不一致时保留两侧证据并保持未关联。

| OTel 节点与 TranscriptItem 证据 | Trace 结果 |
| --- | --- |
| Session ID 缺失/不一致 | `UNMATCHED`，保留失败字段与两侧来源 |
| Session ID 一致，Item `turnId` 与 OTel `(conversation.id, turn.id)` 一致 | Item 到 Turn 的 `EXACT` Evidence Link |
| 精确 Turn 内只有一个工具候选，但公共调用 ID 未验证 | 工具 Item 与 OTel Tool Record 为 `INFERRED`，保存类型、时间和顺序证据 |
| Session、Turn 一致，且版本化适配器验证 OTel 调用 ID 与 Transcript `callId` 的同值语义 | Tool 级 `EXACT` Evidence Link |
| 零个或多个无法消歧的 Tool 候选 | `UNMATCHED`，记录候选数量，不按时间最近项消歧 |

## UNKNOWN 结构与映射

未知记录按“排序后的叶子 JSONPath + JSON 值类型”计算结构指纹。映射使用受限 RFC 9535 JSONPath；支持 `$`、成员、数组下标和 `[*]`，禁止递归下降、过滤、函数与脚本。映射字段、基数校验和示例见技术设计。映射仅改变该结构指纹的标准化结果，不改变原始记录，也不触发 OTel 重放或 JSONL 全量重扫。

## JSONL：Transcript Item 与内容证据

Transcript 独立保存原始记录并转换为不可变 `TranscriptItem`，不将未验证字段推导为 Execution 会话、轮次或关联。S2.1 逻辑模型如下（baseline 1 的 `source_file`、`raw_jsonl_record` 名称在新空 schema 中分别替换为 `transcript`、`transcript_item`）：

- `Transcript`：本地规范 Path、文件身份、generation、已提交字节偏移、检查点之前最多 4096 字节的摘要、观测大小、修改时间、扫描时间和 Transcript Meta。
- `TranscriptItem`：Transcript Path、Session ID 来源快照、generation、行起止字节偏移、SHA-256、原始字节、UTF-8 原文、解析状态、外层事件类型、可解析的 Unix 毫秒时间、已解析 Turn/Call 候选、适配器版本和入库时间。
- `(transcript_path, generation, byte_offset)` 唯一约束与内容摘要共同保证增量重试幂等；文件更换/截断/检查点附近内容变化产生新 generation，不覆盖历史数据。
- `VALID_JSON` 仅表示 UTF-8 有效且整行恰好为一个 JSON 对象；不代表已支持该事件版本。已支持语义由独立的 Item 解析状态和适配器版本表达；未知类型保留，缺少或无效时间戳返回空值，不借用接收时间伪造事件时间。
- `INVALID_JSON` 和 `INVALID_UTF8` 均保留原始字节并继续读取下一行；无效 UTF-8 的展示文本可能含替换字符，以原始字节为准。B1 不标准化事件，不计算 TTFT、完整度、诊断或关联。

检查点附近摘要可发现常见截断后增长和重写，但不对已提交的整个历史前缀反复校验；仅改写更早历史且保持文件身份与检查点附近内容不变的情况不在增量检测保证内。

JSONL 主要回答“同一 OTel Session/Turn 中的可见内容是什么”，可能包含：

- 会话和 Turn 元数据。
- 用户输入和模型可见输出。
- `task_complete.last_agent_message` 形式的最终可见输出。
- 可见 reasoning summary。
- 工具调用、工具参数、工具结果和 `call_id`。
- Token、上下文、compaction 和任务状态。

JSONL 内部可以通过 `turn_id`、`call_id` 等字段建立可靠关系。事件时间戳可以估算阶段耗时，但无法稳定拆分网络、服务端排队、TTFT 和模型推理。

已验证适配器可以在同一 Transcript Path/Session 下，按完全相同的 JSONL `call_id` 配对工具调用与工具结果。Trace 先用公共 Session/Turn 身份把整组内容限定到精确 Turn；其他内部 `turn_id` 仅作为 Transcript 原始证据保留。Codex 0.154.0 本地工具的 OTel `call_id` 已验证与模型级 `custom_tool_call.call_id` 同值，可以产生 `EXACT`；hosted tools、其他版本或没有已验证公共身份的记录，只能用类型、事件时间和顺序产生 `INFERRED` 候选，零个或多个候选保持未链接，不能选择时间最近者。

一个模型 `custom_tool_call` 可以包含多个 `CommandExecution`。目标读模型保留一个 `ModelToolCall` 父节点和多个命令子执行：父节点按模型 Call ID 计数，子执行按 `(transcriptPath, generation, sourceItemOffset, executionId)` 计数；每个子执行保留自己的 `status`、`exitCode` 和内容侧时间区间。父 Tool Call 的正式性能耗时取 OTel 父 Span或经版本验证的 OTel 调用整体区间，不对子执行耗时求和；失败后重试产生新的模型 Call ID，不与单次调用内的并行子执行合并。

## Trace 关联重算与证据生命周期

- `TranscriptEvidenceLink` 和 `ToolAlignment` 是 Trace 拥有的可重算关系，不嵌入 Transcript 或 Execution 聚合，也不写回上游事实。
- 每条关系至少保存两侧稳定身份与 revision、等级、使用字段、算法版本、创建/更新时间；时间关联另保存时间差或重叠证据。
- 上游 revision 变化、适配器/算法版本升级或目标消失时，Trace 先使旧关系失效，再按同一 Turn 重算；API 只返回当前有效版本，原关系可保留为审计证据。
- Transcript Item 或 Execution Record 先到都允许。缺少另一侧时保留未匹配状态；后续任一相关变更都会重新触发该 Turn 的候选评估。
- 一个来源命中多个候选时保持 `UNMATCHED` 并返回候选数量；不得为了让页面完整而选择最接近项。

## OTel：性能调用链

OTel 回答“发生了什么、当前状态是什么、时间花在哪里”。官方已定义 API、SSE、WebSocket、用户提示、工具决策和工具结果等 structured log events，以及 API、流和工具的聚合 metrics；目标版本 logs/traces 经过样本验证后还可能提供更细的性能证据：

- API 和传输请求耗时。
- 流事件、Token 计数，以及有版本证据时的 TTFT、推理时间和 Token 间隔。
- 工具批准/拒绝决策与工具执行耗时。Codex 0.154.0 尚未证明审批等待起点、持续时间和稳定调用身份；V1.0 只保留决策事实，等待耗时为未知，对应未覆盖区间归入未归因。
- 重试、失败和传输回退。
- OTel `trace_id`、`span_id` 和父子关系。

OTel Trace 内部的父子关系是性能链路的权威来源。OTel Log 用来补充运行事件，Metric 用来计算趋势和 P50/P95。Metric 是聚合数据，不能映射到某一次具体工具调用。官方文档另列的匿名 analytics `Metrics` catalog 与 OTel export 相互独立，因此目录名称本身不能作为本地 OTLP 可得证据；目标 Codex 版本真实导出的 OTLP 样本可以形成 `VERSIONED_SAMPLE` 证据，但仍不得用 `turn.ttft.duration_ms`、`mcp.call`、`hooks.run` 等聚合指标重建单次事件。

## 无法默认精确融合的原因

- JSONL 的 `call_id` 和 `turn_id` 没有被保证出现在每一个 OTel Span 中。
- OTel 的 `trace_id` 和 `span_id` 通常不写入 JSONL。
- 一个 OTel 行为节点可能由多个 JSONL 行补齐，一个 Transcript Tool Call 也可能对应多个 OTel 请求、事件或 Span。
- 一个工具行为可能拆成审批、执行和子进程等多个性能阶段。
- 审批决策事件不能冒充审批等待 Span，也不能用决策时间和相邻工具时间反推等待开始；完整审批等待关联与耗时分析属于 V1.1。
- 同名并行工具仅靠时间和工具名无法消除歧义。
- OTel 异步批量发送会影响到达顺序，但不是关联困难的根因；应使用事件自身时间，而不是接收时间。

## 双层时间轴

页面使用共享时间坐标展示两层数据：

```text
OTel 执行层：用户入口 ─ 轮次状态 ─ 工具事件 ─ 最终状态
Transcript 内容层：用户正文 ─ 可见模型消息 ─ 工具参数 ─ 工具结果 ─ 最终正文
OTel 性能层：API ─ 流事件/可验证 TTFT ─ 工具结果/可验证 Span ─ API
```

两层可以同步缩放和定位，但在没有可靠证据时保持为两个节点。

## 关联等级

- `EXACT`：两侧存在相同且经过当前版本验证的公共 ID。
- `BOUNDED`：能够确认属于同一会话或同一 Turn，但不能确定具体事件。
- `INFERRED`：根据事件类型、工具名和时间窗口推测。
- `UNMATCHED`：没有足够证据建立关系。

每条 Alignment 保存：两侧记录 ID、等级、使用的字段、时间差、算法版本和说明。前端不得隐藏该等级。

工具关联面向用户使用以下固定证据文案：

- `精确关联：OTel 与 Transcript 的公共工具调用身份值相同，生产者与格式适配器版本均已验证。`
- `推测关联：同一精确 Turn 内工具类型相同，时间重叠 92%，且调用顺序唯一；公共调用身份尚未验证。`

## 示例

假设 JSONL 显示：

```text
00.0s 用户要求运行测试
06.0s 发起 shell 调用 call_demo_7
36.0s 收到 shell 结果
42.0s 输出最终回复
```

JSONL 可以估算工具用了约 30 秒，但最初 6 秒和最后 6 秒无法进一步可靠拆分。

同一会话的 OTel 显示：

```text
00.2s—05.9s API 请求，TTFT 4.1s
06.1s—36.1s shell 性能 Span
36.3s—41.8s 第二次 API 请求
```

如果 OTel shell Span 也携带经过验证的 `call_demo_7`，两者建立 `EXACT` 关联。若只有 `tool=shell` 和时间重叠，则最多建立 `INFERRED` 关联。页面仍可得出“约 30 秒集中在工具阶段”的性能判断，但不能声称两个节点必然代表同一调用。

## 缺失与异步场景

- 只有 JSONL：可以形成 Transcript、TranscriptMeta 和 TranscriptItem，并用于来源健康、原始记录与 UNKNOWN 检查；不生成 Execution 行为层、正式 Trace 或分析会话。
- 只有 OTel：建立正式执行与性能 Trace，内容状态显示缺失。
- OTel 先到：先建立执行与性能 Trace；Transcript 到达后按 Session/Turn 身份重算内容链接。
- JSONL 先到：独立形成 Transcript/TranscriptItem，只进入来源健康、原始记录与内容检查；OTel 到达后由 Trace 依据 Session、Turn 和 Tool 证据重算关联。
- 多个候选：保持未匹配或展示候选数量，不自动选取最接近的一条冒充精确关系。
- 解析失败：保留原始事件并显示不支持的输入版本。

## 诊断原则

关键路径和正式性能诊断只基于 OTel Trace 计算，并行区间不能重复累计。没有 OTel Session/Turn 骨架时不创建正式 Trace，也不使用 JSONL 时间戳生成性能诊断；Transcript 检查区可以展示内容侧时间区间，但必须明确它不是 API、TTFT、父子 Span 或正式工具性能。诊断结果必须附带来源和可信度，证据不足时显示数据缺失，不生成确定性结论。
