# 数据与关联模型

## 基本原则

Hook、JSONL 和 OTel 是目的不同、独立产生的三类输入。Hook 建立行为骨架，JSONL 补齐可见内容，OTel 补齐性能；当前公开接口没有保证三者共享事件级主键，因此产品不能把时间接近当成确定关联。

Codex Hook 是标准会话、轮次与工具骨架的主事实来源，但不承诺历史重放或覆盖所有工具。JSONL 只能通过 Hook 的 `transcript_path` 绑定到该骨架并补齐可见内容；OTel 补齐精确性能。任一补充来源缺失都以能力降级表达，不能由 JSONL 独立推导正式会话骨架。

## 标识符命名空间

- Hook command stdin 的官方公共字段中，`session_id` 标识会话，`transcript_path` 为可空字符串。Turn-scoped 事件另含 `turn_id`；只有 `PreToolUse` 与 `PostToolUse` 获得 `tool_use_id` 保证，`PermissionRequest` 没有该保证。
- 已验证的 JSONL 格式在文件第一条 `session_meta` 记录的 `$.payload.session_id` 保存会话 ID；后续普通事件行不保证重复该字段。样本常见 `turn_id` 位于 `$.payload.internal_chat_message_metadata_passthrough.turn_id`，工具结果关联键常见为 `$.payload.call_id`。
- `tool_use_id` 与 JSONL `call_id` 属于不同命名空间，不能仅因用途相似就声明相同。只有采集样本验证两者值相等且语义稳定时才可作为 `EXACT` 证据，否则只能结合会话、轮次、类型和时间形成候选。

## Hook 事件关联

Hook 事件使用复合键合并而不是到达序列：Turn 使用 `(session_id, turn_id)`，工具使用 `(session_id, turn_id, tool_use_id)`。Post 先到时建立局部记录；Pre 后到补齐。若同键重复事件内容冲突，保留全部原始事件和冲突证据，标准状态只单调推进。Hook Pre/Post 耗时属于估算值，只有时间边界完整且非负时才可进入统计，并始终低于 OTel 精确 Span 的优先级。

官方 Hook stdin 未声明来源事件时间戳，因此上述 Pre/Post 边界使用 forwarder 的本地 `observedAt`。审批 Hook 缺少官方 `tool_use_id` 保证，只能先按 `(session_id, turn_id)` 建立 Turn 内审批事件；若没有额外已验证的公共 ID，不提升为具体工具调用的精确关联。

## transcript 文件级关联

Hook 提供的 `transcript_path` 可以在不解析内容的情况下，把 `session_id` 与一个 JSONL 来源文件建立文件级绑定；路径必须位于显式配置根目录内且为可读、非符号链接的普通文件。事件级关联仍必须逐行解析 JSONL。transcript 内容升级导致已知适配器失配时，文件级绑定不失效，行记录按 UNKNOWN 原样保存。

对当前已验证格式，文件级绑定在读取首条完整记录后进一步校验：`type=session_meta` 且 `$.payload.session_id == Hook.session_id`。三者一致时为精确会话关联；缺少元数据、格式未知或 ID 不一致时保留路径绑定证据，但禁止把内容挂到该会话，直到人工处理或适配器升级。

## UNKNOWN 结构与映射

未知记录按“排序后的叶子 JSONPath + JSON 值类型”计算结构指纹。映射使用受限 RFC 9535 JSONPath；支持 `$`、成员、数组下标和 `[*]`，禁止递归下降、过滤、函数与脚本。映射字段、基数校验和示例见技术设计。映射仅改变该结构指纹的标准化结果，不改变原始记录，也不触发 Hook 重放或 JSONL 全量重扫。

## JSONL：Hook 行为节点的内容补齐

后端 B1 先保存原始记录，不将未验证的字段推导为会话、轮次或关联。持久化模型如下：

- `source_file`：本地规范路径、文件身份、generation、已提交字节偏移、检查点之前最多 4096 字节的摘要、观测大小、修改时间、扫描时间。
- `raw_jsonl_record`：自增 ID、来源 ID、generation、行起止字节偏移、SHA-256、原始字节、UTF-8 原文、解析状态、外层事件类型、可解析的 Unix 毫秒时间、入库时间。
- `(source_id, generation, byte_offset)` 唯一约束与内容摘要共同保证增量重试幂等；文件更换/截断/检查点附近内容变化产生新 generation，不覆盖历史数据。
- `VALID_JSON` 仅表示 UTF-8 有效且整行恰好为一个 JSON 对象；不代表已支持该事件版本。未知类型保留，缺少或无效时间戳返回空值，不借用接收时间伪造事件时间。
- `INVALID_JSON` 和 `INVALID_UTF8` 均保留原始字节并继续读取下一行；无效 UTF-8 的展示文本可能含替换字符，以原始字节为准。B1 不标准化事件，不计算 TTFT、完整度、诊断或关联。

检查点附近摘要可发现常见截断后增长和重写，但不对已提交的整个历史前缀反复校验；仅改写更早历史且保持文件身份与检查点附近内容不变的情况不在增量检测保证内。

JSONL 主要回答“Hook 节点对应的可见内容是什么”，可能包含：

- 会话和 Turn 元数据。
- 用户输入和模型可见输出。
- 可见 reasoning summary。
- 工具调用、工具参数、工具结果和 `call_id`。
- Token、上下文、compaction 和任务状态。

JSONL 内部可以通过 `turn_id`、`call_id` 等字段建立可靠关系。事件时间戳可以估算阶段耗时，但无法稳定拆分网络、服务端排队、TTFT 和模型推理。

## OTel：性能调用链

OTel 主要回答“时间花在哪里”，可能包含：

- API 和传输请求耗时。
- TTFT、推理时间和 Token 间隔。
- 工具审批与工具执行耗时。
- 重试、失败和传输回退。
- OTel `trace_id`、`span_id` 和父子关系。

OTel Trace 内部的父子关系是性能链路的权威来源。OTel Log 用来补充运行事件，Metric 用来计算趋势和 P50/P95。Metric 是聚合数据，不能映射到某一次具体工具调用。

## 无法默认精确融合的原因

- JSONL 的 `call_id` 和 `turn_id` 没有被保证出现在每一个 OTel Span 中。
- OTel 的 `trace_id` 和 `span_id` 通常不写入 JSONL。
- 一个 Hook 行为节点可能由多个 JSONL 行补齐，也可能对应多个 OTel 请求、事件或 Span。
- 一个工具行为可能拆成审批、执行和子进程等多个性能阶段。
- 同名并行工具仅靠时间和工具名无法消除歧义。
- OTel 异步批量发送会影响到达顺序，但不是关联困难的根因；应使用事件自身时间，而不是接收时间。

## 双层时间轴

页面使用共享时间坐标展示两层数据：

```text
Hook 行为层：用户入口 ─ 轮次状态 ─ 工具调用 ─ 工具结果 ─ 最终状态
JSONL 内容： 用户正文 ─ 可见模型消息 ─ 工具参数 ─ 工具结果 ─ 最终正文
OTel 性能层：API ─ TTFT ─ 推理 ─ 审批 ─ 工具 Span ─ API
```

两层可以同步缩放和定位，但在没有可靠证据时保持为两个节点。

## 关联等级

- `EXACT`：两侧存在相同且经过当前版本验证的公共 ID。
- `BOUNDED`：能够确认属于同一会话或同一 Turn，但不能确定具体事件。
- `INFERRED`：根据事件类型、工具名和时间窗口推测。
- `UNMATCHED`：没有足够证据建立关系。

每条 Alignment 保存：两侧记录 ID、等级、使用的字段、时间差、算法版本和说明。前端不得隐藏该等级。

工具关联面向用户使用以下固定证据文案：

- `精确关联：Hook tool_use_id 与 JSONL call_id 值相同，格式适配器版本 codex-2026-09 已验证。`
- `推测关联：标识不同；同一 Turn 内工具类型相同，时间重叠 92%。`

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

- 只有 JSONL：仅展示原始记录与 UNKNOWN 解析检查，不生成正式行为层或分析会话。
- 只有 OTel：仅进入未绑定性能记录检查区；没有 Hook 骨架时不生成正式分析会话。
- OTel 先到：先保存为未绑定性能记录；Hook 到达并建立骨架后再计算候选 Alignment。
- JSONL 先到：先保存原始记录；Hook 通过 transcript_path 绑定后才补齐正式行为节点。
- 多个候选：保持未匹配或展示候选数量，不自动选取最接近的一条冒充精确关系。
- 解析失败：保留原始事件并显示不支持的输入版本。

## 诊断原则

关键路径优先基于 OTel Trace 计算；没有 OTel 时才使用 JSONL 估算区间。并行区间不能重复累计。诊断结果必须附带来源和可信度，低可信度结论使用“可能”“疑似”等表达。
