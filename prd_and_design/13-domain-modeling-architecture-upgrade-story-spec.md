# Story2.1：问题域与聚合边界架构升级

## Story 信息

- 编号：E1-S2.1。
- 名称：问题域与聚合边界架构升级。
- 优先级：P0；原为 E1-S2 完成后的下一 Story，当前让位于 E1-S2.2 数据源方案决策。
- 状态：暂停；S2.1-S2 Hook-first Execution 重构已完成自动测试，但用户于 2026-09-18 确认 V1.0 采用 OTel + Transcript、OTel-first，原实现不再作为目标架构验收。完成 S2.2 规格一致性与 V3 门禁后，将另行拆分迁移 Story；现有实现只保留作回归和回滚基线。
- 登记记录：用户于 2026-09-16 指出当前设计首先没有澄清问题域，要求作为独立技术架构升级 Story，在当前 E1-S2 完成后处理。
- 启动记录：用户于 2026-09-16 明确启动，并确认问题域、聚合原则、兼容范围和交付顺序。
- 前置门禁：E1-S2 必须完成 `IT-HOOK-TRACE-002`、代码阅读并由用户明确接受。
- 前置门禁状态：已满足；E1-S2 于 2026-09-16 由用户明确接受。
- 后续门禁：E1-S2.2 完成规格一致性评审和用户确认，且 OTel-first 迁移 Story 通过验收前，不启动后续性能与实时功能 Story。

## 背景与已发现问题

当前代码已经按 `hook-normalization`、`transcript-content` 和 `trace-query` 等技术流程拆分目录，但该拆分尚不能证明问题域、限界上下文和聚合边界已经成立。当前至少存在以下待验证问题：

1. `SessionLifecycle`、`TurnLifecycle` 和 `ToolLifecycle` 分别通过 Repository 独立加载和保存，但部分注释又把 Turn、Tool 表述为 Session 内部 Entity，聚合根身份和一致性边界相互矛盾。
2. `HookTranscriptTargetRepository` 同时读取 Session、Turn 和 Tool，只提供候选与存在性查询，不保存任何聚合；它更接近跨聚合读模型或查询端口，`Repository` 命名掩盖了真实职责。
3. transcript 内容补齐需要读取 Hook 骨架、判断 Turn/Tool 目标并保存 supplement，但当前设计没有先明确这些概念分别属于哪个问题域、上下文之间通过什么契约协作。
4. 目录分层、数据库表和处理步骤不能替代领域建模；在问题域未澄清前继续增加 Repository 或领域服务，可能扩大错误边界。

上述内容是架构问题清单，不预设最终必须合并或继续拆分 Session、Turn、Tool。最终方案必须由领域不变量、生命周期、并发和事务要求推导。

## 已确认的问题域

后端继续采用分层优先目录：`interfaces/<domain>`、`application/<domain>`、`domain/<domain>`、`infrastructure/<domain>`。层内按以下问题域组织：

| 问题域 | 类型 | 业务职责 | 明确不负责 |
| --- | --- | --- | --- |
| Execution | 核心域 | 接收并归一化 Hook 事实；维护 Session、Turn、Tool Call 生命周期 | 不读取 transcript 文件，不解析 OTLP，不组装跨源 Trace |
| Transcript | 支撑域 | 独立发现并增量读取 Codex transcript；维护 Transcript Meta；生成携带 Path/Session 来源的 Transcript Item | 不创建 Execution Session/Turn/Tool/Trace，不查询 Hook 表决定挂接目标 |
| Telemetry | 支撑域 | 接收、解析和保存 OTLP 对象；提供按 Turn 查询和变更查询 | 不自行决定与 Execution 节点的关联等级 |
| Trace | 核心域 | 关联 Execution、Transcript、Telemetry 证据；生成 Turn 粒度 Trace 读模型 | 不拥有三种来源的采集和协议解析 |
| Operations | 支撑域 | 汇总采集状态和运行指标 | 日志是横切基础设施，不作为领域实体或领域状态 |

Hook 是 Execution 的入站协议，不再作为同时拥有 transcript、OTel 和查询能力的大问题域。用户查看和分析的主要入口是 Turn；Trace 查询必须从 Turn 开始组合读模型，不通过 Session 聚合加载完整对象图。

## 已确认的聚合边界

聚合采用“小聚合优先、操作始终从根进入”的原则。父子关系、外键或页面同时展示不能作为扩大聚合的理由。

| 聚合根 | 所属域与业务身份 | 生命周期/状态 | 核心不变量与操作入口 | Repository |
| --- | --- | --- | --- | --- |
| `CodexSession` | Execution；`sessionId` | `UNKNOWN -> RUNNING -> COMPLETED/INTERRUPTED`，保留 Hook `transcriptPath` 证据 | 终态不重开；空路径不覆盖已知路径；Hook Session 事实只能经 `applySessionFact` 进入 | `CodexSessionRepository` |
| `CodexTurn` | Execution；`(sessionId, turnId)` | `UNKNOWN -> RUNNING -> COMPLETED/INTERRUPTED` | 终态不回退；相同 `turnId` 可存在于不同 Session；Hook Turn 事实经 `applyTurnFact` 进入 | `CodexTurnRepository` |
| `ToolCall` | Execution；`(sessionId, turnId, toolUseId)` | `UNKNOWN -> RUNNING -> SUCCESS/FAILED/INTERRUPTED` | Pre/Post 任意顺序；终态不回退；只有完整非负边界产生估算耗时；经 `applyToolFact` 进入 | `ToolCallRepository` |
| `RawHookEvent` | Execution；`deliveryId` | `PENDING -> NORMALIZED/UNKNOWN/FAILED` | 原始证据只追加且 delivery 幂等；HTTP 用例经 `acceptHookDelivery` 创建 | `RawHookEventRepository` |
| `HookNormalizationJob` | Execution；`(sourceKind, sourceId)` | `PENDING -> RUNNING -> COMPLETED/FAILED` | 尝试次数单调；稳定失败类别；与 Raw Event 同事务创建，Scheduler 经 `normalizeNextHookDelivery` 推进 | `HookNormalizationJobRepository` |
| `Transcript` | Transcript；安全规范化后的 Path | generation、检查点、来源健康和 Transcript Meta 单调演进 | 只消费完整行；文件替换/截断开启新 generation；首条支持的 `session_meta` 形成 Meta；Item 与检查点同事务；经 `commitTranscriptBatch` 进入 | `TranscriptRepository` |
| `TranscriptItem` | Transcript；`(transcriptPath, generation, byteOffset)` | 创建后不可变，可增加版本化解析结果 | 保存 Path 与 Transcript Meta Session ID 快照；原始字节/文本不可覆盖；解析失败或 UNKNOWN 仍保留；经 `appendTranscriptItem` 创建 | `TranscriptItemRepository` |
| `TelemetryRecord` | Telemetry；协议身份或 `(batchId, objectIndex)` | 创建后不可变，可增加版本化解析结果 | 三类信号分开保存；原始对象不可覆盖；经 `acceptTelemetryRecord` 创建 | `TelemetryRecordRepository` |
| `TranscriptEvidenceLink` | Trace；`(itemId, targetType, sessionId, turnId, toolUseId?, algorithmVersion)` | `ACTIVE/STALE`，可重算 | 目标始终保存完整复合身份；等级只来自可检查证据；重复变化幂等；经 `linkTranscriptItemToTurn/ToolCall` 进入 | `TranscriptEvidenceLinkRepository` |
| `TelemetryAlignment` | Trace；`(recordId, targetType, sessionId, turnId, toolUseId?, algorithmVersion)` | `ACTIVE/STALE`，可重算 | 保存完整目标身份、等级、字段、时间差和算法版本；当前未验证公共 ID 时禁止 `EXACT`；经 `alignTelemetryRecordToExecutionNode` 进入 | `TelemetryAlignmentRepository` |
| `TraceProjectionCheckpoint` | Trace；`(changeSource, consumerName)` | 单调 sequence | 只有关系和 Turn Trace 同事务提交后才能推进；经 `advanceTraceProjection` 进入 | `TraceProjectionCheckpointRepository` |

一个 Hook 事件允许由 Application 在一个有界事务内更新 Session、Turn、Tool 三个小聚合，以保证同一原始事件完整应用或整体重试；每个聚合仍独立加载和保存，事务最多触及本事件对应的三个根。Transcript 批次只在“新增完整行和推进文件检查点”之间保持原子性，批量大小受配置限制。

`TranscriptMeta` 是 Transcript 内的值对象，至少包含 Session ID、适配器版本和 `session_meta` Item 位置；它不是独立实体，也不包含 Hook 关联结果。Transcript 上下文的核心实体只有 `Transcript` 与 `TranscriptItem`。UNKNOWN 结构指纹、映射版本和聚合统计是从 Item 派生的管理配置/查询模型，不进入 Transcript 聚合边界；单指纹重新标准化由 Application 编排映射配置、Item 查询和解析策略。`TurnTrace`、会话列表项、采集状态和跨域候选同样是 Read Model，不是聚合根。

## 已确认的上下文关系

```text
Execution (上游/Published Language) -- ExecutionNodeQuery + ChangeFeed ----+
Transcript (上游/Published Language) -- TranscriptItemQuery + ChangeFeed --+--> Trace
Telemetry  (上游/Published Language) -- TelemetryRecordQuery + ChangeFeed -> Trace
Execution / Transcript / Telemetry -- 脱敏状态摘要 ----------------------> Operations
Trace -- TraceViewQuery -------------------------------------------------> HTTP API
```

- 上游 DTO 只包含稳定身份、revision、状态和查询需要的证据摘要，不暴露聚合方法、Repository 或数据库 Row。
- Change Feed 在上游业务写事务中产生单调序列；Trace 至少一次消费并用自己的持久化检查点恢复，不能依赖仅存在于进程内的通知。
- Trace 对每个来源建立防腐转换，不允许 Trace Domain 依赖 Hook、Jackson、OTLP 或 MyBatis 类型。
- Operations 只读组合状态，不可反向改变采集、解析、关联或生命周期结果。审计日志继续由基础设施切面处理。

## 已确认的跨域端口

- `ExecutionNodeQuery`：按 `(sessionId, turnId)` 返回 Hook 建立的 Session/Turn/Tool 节点及原始 `transcriptPath` 证据。
- `ChangedExecutionTurnQuery`：返回发生生命周期变化的 Turn 标识。
- `TranscriptQuery`：按安全规范 Path 查询 Transcript Meta、来源健康和 revision。
- `TranscriptItemQuery`：按 Path、Session ID 和 Turn 候选查询携带完整来源信息的 Transcript Item。
- `ChangedTranscriptTurnQuery`：通过持久化游标返回存在新增 Item 的 Turn 标识。
- `TelemetryRecordQuery`：按 `sessionId + turnId` 查询 Telemetry Record。
- `ChangedTelemetryTurnQuery`：通过持久化游标返回存在新增遥测的 Turn 标识。
- `TraceViewQuery`：返回兼容现有 HTTP DTO 的 Turn Trace 读模型。

Repository 只服务写模型聚合。跨聚合和跨问题域的只读端口统一使用 `Query`、`Lookup`、`ReadModel` 或 `ChangeFeed` 业务命名，不再以 `Repository` 掩盖职责。

## 可读性设计

- 类和方法必须以业务命令或查询命名。现有 `save` 应拆为 `linkTranscriptItemToTurn`、`linkTranscriptItemToToolCall`；`processAvailable` 应按用例改为 `normalizeNextHookDelivery`、`refreshTranscriptItems` 或 `linkChangedTranscriptTurns`。
- `scanFile` 的职责拆为识别文件代次、读取新增完整行、解析 Transcript Item、原子提交 Item 与检查点等具名步骤，不用一个私有方法隐藏全部工作原理。
- 每个业务类使用中文类级 Javadoc 说明职责、所有权、不变量和非职责；Controller、Scheduler、Use Case、Domain Service/Policy 的公开业务方法说明目的、输入前置条件、结果、事务和失败语义。
- 复杂私有算法注释业务原因、关键步骤和不明显的不变量，不复述语句。Codex Hook、目录、transcript 和 OTLP 格式规则集中在版本化适配器及契约类型，并引用对应 fixture。
- Domain 不依赖审计注解。日志切面按 Controller、Scheduler、UseCase、Domain Service/Policy 的明确命名约定织入，继续满足脱敏日志规则。

## 兼容与数据决策

- S2.1 只重构 S1、S2 和现有遗留 OTel 骨架，不提前实现 S3 的新性能能力。
- HTTP 响应语义保持兼容；为表达 Turn 复合身份，详情 URL 改为 `/api/sessions/{sessionId}/turns/{turnId}/analysis`，正式前端同步迁移。CLI 协议保持兼容。
- 使用新的空 MySQL schema，不迁移或回填现有本机数据。应用不得自动清库；数据库表由用户明确创建或重建。
- 新 schema 按上述小聚合、跨源 Evidence Link、Alignment 和持久化消费游标设计。启动时校验 schema 版本，不匹配时返回稳定配置错误。
- 当前没有经过版本化样本验证的 Hook/OTel 公共调用 ID。遗留 OTel 关联迁移到 Java 后只能产生 `BOUNDED` 或 `UNMATCHED`；S3 验证契约后才允许产生 `EXACT`。

## S2.1-S1 一致性检查结果

### PRD、V2 与新方案

| 检查项 | 结论 | 设计处理 |
| --- | --- | --- |
| Hook-first 与三源职责 | 一致 | Execution 继续是行为骨架唯一所有者；Transcript/Telemetry 只发布证据，Trace 负责组合 |
| 页面与 API 入口 | 视觉交互一致，API 身份需修正 | Turn 仍是列表和 Trace 主对象；详情 URL 改为携带 `sessionId + turnId`，响应字段语义和冻结 V2 视觉不变 |
| `turn_id` 身份 | 已澄清 | `(sessionId, turnId)` 才是全局唯一业务身份；数据库、Query、Change Feed 和 Trace 链接不得只使用 `turnId` |
| Transcript 解析与关联 | S2.1 升级技术语义 | Transcript 独立解析；Transcript/Item 均保存 Path 与 Session 来源；Trace 使用 Hook 节点直接关联 Item，不再建立 Binding |
| JSONL 工具内容 | 一致 | 已验证 transcript 同值契约才允许 Tool `EXACT`，否则唯一 Turn `BOUNDED`，歧义不挂接 |
| OTel 工具关联 | 现实现违反数据规则 | baseline 2 移除 SQL `EXACT`；S3 验证公共 ID 前只允许 `BOUNDED/UNMATCHED` |
| 完整度、耗时和诊断 | 产品口径不变 | Trace 读模型继续返回兼容字段；S2.1 只迁移既有能力，不提前补齐 E1-S3/E2 能力 |
| 原型 | 无交互变化 | 不创建 V3，不修改 `prototype-v1`/`prototype-v2` |

PRD 已同步 Turn 复合身份、Transcript 独立解析和 Hook-to-TranscriptItem 关联语义。该变化不改变冻结 V2 的页面结构和交互方式，因此不创建 V3、不修改冻结原型；用户确认本设计门禁后才能进入编码。

### 当前实现差异清单

| 编号 | 当前实现证据 | 与目标方案的差异/风险 | 处理子 Story |
| --- | --- | --- | --- |
| GAP-01 | `api/`、`ingestion/`、`persistence/` 与四层目录并存 | 技术分包绕过限界上下文，职责和依赖方向不可约束 | S2.1-S2 至 S5 渐进移除 |
| GAP-02 | `IngestionMapper` 同时访问 JSONL、Hook、OTel、UNKNOWN、列表和 Alignment | 一个 Mapper 跨五个上下文并含查询/写入/业务规则 | 各子 Story 按所有权拆 Mapper，S5 删除 |
| GAP-03 | `SessionLifecycle` 是根，`TurnLifecycle`/`ToolLifecycle` 注释仍称 Entity；Repository 方法统一叫 `save` | 代码语义与三个小聚合决策矛盾，调用点无法表达业务命令 | S2.1-S2 重命名聚合、Repository 方法和用例 |
| GAP-04 | `NormalizeHookEventUseCase.processAvailable` 一次直接调用三个 Lifecycle Service | 事务范围基本符合目标，但名称、任务状态竞争和复合 Turn 身份未在所有查询中贯彻 | S2.1-S2 先写特征/并发测试再重构 |
| GAP-05 | `JsonlScanner` 同时负责启动/调度、遍历、监听、文件代次、读取、解析和持久化 | Transcript 聚合与适配器边界缺失，私有大方法隐藏检查点不变量 | S2.1-S3 拆调度、发现、增量读取、解析和 Repository |
| GAP-06 | `HookTranscriptTargetRepository` 位于 Transcript Domain 并直接查询 Hook Session/Turn/Tool 表 | Transcript 依赖 Execution 持久化模型，且只读端口被命名为 Repository | S2.1-S3 删除该依赖；Transcript 独立生成带来源的 Item |
| GAP-07 | `TranscriptContentService` 同时选择 Hook 目标并保存 `jsonl_supplement` | Evidence Link 所有权错误地位于 Transcript，跨域关联无法独立重算 | S2.1-S3 只保存 Transcript/Item；S4 将 Hook-to-Item 策略迁至 Trace |
| GAP-08 | `TraceAnalysisRepository` 实际是跨表只读查询，`GetTraceAnalysisUseCase` 直接组合 Transcript Repository | Read Model 端口被伪装为 Repository，Trace 没有独立投影/检查点 | S2.1-S4 建 `TraceViewQuery` 与投影用例 |
| GAP-09 | `OtelController`/`OtelIngestionService` 使用裸 `byte[]`、`JsonNode`、`Map`，服务直接访问通用 Mapper | REST、Application、Domain 和协议适配未分层 | S2.1-S4 建结构化接收 DTO、Record 聚合与适配器 |
| GAP-10 | `IngestionMapper.alignExactOtelTools` 以 `tool_use_id == call_id` 直接插入 `EXACT` | SQL 承载关联规则，且违反未验证命名空间约束 | S2.1-S4 删除 SQL；Java Trace Policy 只产出 `BOUNDED/UNMATCHED` |
| GAP-11 | 当前无上游 change log 或 Trace 消费检查点 | 后到数据、重启和重复调度只能靠全表查询/进程行为，无法证明收敛 | S2.1-S2 至 S4 逐域建立 Change Feed，S4 建投影检查点 |
| GAP-12 | `schema.sql` 是 baseline 1 表；`DatabaseMigrator` 只插入版本 1，不拒绝不匹配 schema | 启动可能在错误结构上运行，且“迁移器”命名暗示应用执行升级 | S2.1-S2 先建 baseline 2 验证器；DDL 由用户显式应用 |
| GAP-13 | Domain 类型依赖 `AuditedBusinessOperations` 注解 | Domain 依赖日志基础设施概念，破坏纯净边界 | S2.1-S2/S3/S4 移除，切面按明确类型约定织入 |
| GAP-14 | `hook_turn` 正确使用 `(session_id, turn_id)`，但部分 Trace SQL 和当前 URL 只按 `turn_id` 查询 | 丢失 Session 维度后可能命中另一个合法 Turn | S2.1-S2 增加跨 Session 同 `turnId` 测试；S4/前端迁移复合身份 API |
| GAP-15 | SQL 从 Raw Hook JSON 提取 title/model/cwd，并跨上下文拼装列表 | Mapper 承担协议解释和读模型业务规则，升级协议时散落 | S2.1-S2 结构化 Execution 字段，S4 由 Trace 投影组合 |
| GAP-16 | Hook/OTel 状态计数主要保存在进程内 | 只适用于单进程运行期；不影响本次功能兼容，但不能宣称长期/高可用统计 | S2.1-S5 归入 Operations；持久化升级仍留在 E2-S1 |

### 编码前门禁结论

- 阻塞项：独立 Transcript 解析、复合 Turn 身份和 Hook-to-TranscriptItem 关联已由用户澄清；仍需把修订后的完整方案与 baseline 2、Change Feed/检查点、OTel 非 `EXACT` 规则一并确认。
- 强烈建议项：按 S2.1-S2 至 S5 顺序迁移，每个子 Story 先写兼容特征测试和目标接口测试，完成自动回归与代码阅读后再开始下一个；不采用一次性全仓重写。
- 数据门禁：编码前由测试先证明 schema baseline 不匹配会稳定启动失败；需要数据库操作时，由用户显式重建空 schema，应用和测试不得自动删除现有库表。
- 回滚策略：S2.1 不保留 baseline 1 数据兼容或双写。每个子 Story 的代码与同版本空 schema 成对回滚；若需回到旧代码，使用另一个明确的空 baseline 1 测试库，不在原库原地降级。

## Story 目标

先完成领域发现和边界设计，再调整代码，使业务概念、聚合一致性边界、读写职责和代码命名相互一致：

- 明确核心域、支撑域及其业务能力，不以采集步骤或框架分层代替问题域。
- 建立统一语言，明确 Session、Turn、Tool Call、Transcript、TranscriptItem、Evidence 和 Alignment 的含义与所有权。
- 绘制限界上下文及上下文关系，定义 Hook 骨架、Transcript 内容和 Trace 查询之间的稳定契约。
- 按必须保持原子一致的业务不变量决定聚合根；明确哪些对象可独立演进，哪些只能通过聚合根修改。
- 每个写模型聚合根只暴露一个语义明确的 Repository；跨聚合查询使用 Query/Lookup/Read Model 端口，不伪装成聚合 Repository。
- 使事务、并发控制、幂等和乱序处理与新的聚合边界一致。

## 子 Story 与交付顺序

### S2.1-S1 领域设计基线（已完成、已确认）

- 将本 Story 的问题域、关键实体、聚合、端口、兼容性、数据库和测试决策同步到技术设计、数据关联模型、Epic 台账与协作规则。
- 对照 PRD、V2、Hook/Transcript 契约和当前实现完成一致性检查。
- 本阶段只修改规格；用户确认设计门禁后才进入代码。

### S2.1-S2 Execution 重构

- 当前状态：自动测试完成、人工验收暂停。Execution 实现已完成；`codex_analyze_test` 已重建并验证为 baseline 2，共 19 张上下文自有表；JDK 17/MySQL 完整后端测试 61 项全部通过，其中 `IngestionIntegrationTest` 22 项全部通过；Mapper XML 和 `git diff --check` 已通过。2026-09-17 开始执行下述 OTel-only 复核，结论确认前不把当前 Hook-first 实现作为已验收基线。
- 重构 Hook 接收、归一化及 Session/Turn/Tool 三个小聚合。
- 保持 Hook API、幂等、乱序、终态保护、失败重试和有界事务语义。
- 删除通过 Session 导航或加载 Turn/Tool 的可能性。

### S2.1-D1 OTel-only 可行性采样（已移交 S2.2 并完成总体决策）

- 背景：用户提出 OTel 可作为行为与性能主来源并直接关联 transcript；跨源 `call_id` 不要求相等，允许使用事件时间、Session/Turn 边界、工具类型和顺序形成有等级的候选关系。
- 公开契约检查：OTLP 的传输结构是公开协议；Codex 生产者实际发送的事件名、属性、覆盖范围和版本稳定性必须另行确认。OpenAI 官方在线文档在本次环境中返回 HTTP 403，不能据此断言完整字段契约；本机 `codex-cli 0.154.0` 配置模式确认支持 OTLP/HTTP binary 的 log、trace、metric exporter。
- 采样方式：独立本机接收器监听 `127.0.0.1`，分别保存 `/v1/logs`、`/v1/traces`、`/v1/metrics` 的原始 protobuf、请求元数据和 `protoc --decode_raw` 结果；采样数据写入已忽略的 `otel-data/`，不得提交真实载荷。
- 样本场景：至少执行一次普通回复、一次工具成功、一次工具失败或中断；同时定位同一会话的 transcript，只输出字段结构、哈希和时间差，不把正文、路径、账号或凭据写入仓库。
- 判定项：是否存在稳定的 Session/Thread、Turn、Call 身份；行为开始/终态是否齐全；工具类型、失败/中断和事件自身时间是否可用；OTel 与 transcript 能否通过共同身份或有界时间/顺序规则唯一关联；exporter 缺失时产品如何降级。
- 决策结果：用户于 2026-09-18 确认 V1.0 选择 OTel + Transcript、OTel-first；Hook 不进入目标架构，也不作为可选降级。完成规格一致性和 V3 原型规格门禁前仍不删除 Hook、不改写 schema、不启动代码迁移。
- 首轮结果：Codex 0.154.0 已实际发送 OTLP/HTTP binary 的 logs、traces、metrics。失败会话的 OTel `conversation.id` 与其 transcript Session ID 完全一致，OTel `turn.id` 与 transcript Turn ID 完全一致，因此 Session/Turn 关联不需要 Hook 或时间推断。样本同时覆盖启动、WebSocket 重试、HTTP 降级、API 请求和认证失败。
- 当前门禁：本机 Codex 认证失败发生在模型返回前，两次采样均未产生工具调用，尚不能验证 OTel 工具事件是否包含 transcript `call_id`、其他调用身份，或只能使用同一 Session/Turn 内的时间、工具类型和顺序证据。该问题不再阻塞总体架构选择，但阻塞 Tool `EXACT`、工具关联实现和最终代码交付。
- Story 归属：该发现已形成独立的 E1-S2.2 OTel-first 数据源方案升级 Story。S2.1 不再承载总体数据源决策；S2.2 验收后再决定恢复、改写或终止 S2.1 的后续子 Story。

### S2.1-S3 Transcript 重构

- 将 `JsonlScanner` 拆为调度、文件发现、增量读取、契约解析和持久化适配器。
- 建立以 Path 为业务身份的 Transcript、TranscriptMeta 值对象、携带 Path/Session 来源快照的 TranscriptItem，以及 Turn 候选查询和变更查询。
- Trace 负责用 Hook 节点关联 Item；Transcript 不查询 Hook 表。

### S2.1-S4 Telemetry 与 Trace 重构

- 拆分 OTLP Controller、接收用例、解析器、Telemetry Repository 和查询端口。
- 删除 SQL 中的 `alignExactOtelTools`，关联等级、证据和算法版本由 Trace 领域策略决定。
- Trace 查询从 Turn 开始组合各域读模型，不通过 Session 聚合读取。

### S2.1-S5 Operations 与遗留清理

- 统一采集状态、运行指标和审计日志边界。
- 删除旧 `api/ingestion/persistence` 技术分包、通用 `IngestionMapper` 和重复兼容实现。
- 完成全链路回归、代码阅读和人工验收。

每个子 Story 单独按 TDD 实施、更新进度并完成人工验收，不一次性越过中间门禁。

## 非目标

- 不在领域分析完成前直接合并或拆分 Repository。
- 不借本 Story 提前实现 OTel、SSE、指标诊断或其他 E1-S3 以后功能。
- 不修改冻结的 V1/V2 原型；若领域调整改变产品语义或交互，必须先更新 PRD 并建立新原型版本。

## 启动一致性门禁

正式编码前必须由用户确认以下产出：

1. 问题域划分和统一语言不存在影响产品语义的歧义。
2. 每个聚合根的身份、边界和事务不变量均有明确说明。
3. Repository 与 Query/Lookup 端口的命名和职责规则已经确定。
4. 新方案与 PRD、V2、数据关联规则及现有真实主链路的差异已逐项列出。
5. 迁移 Story、测试矩阵和人工回归顺序已经确认。

存在阻塞冲突时，只更新规格并请求决策，不进入代码修改。

## 初始验收标准

- 领域分析产出可解释为什么某个对象是聚合根，而不是仅依据数据库表或现有类结构。
- 不再同时把 Turn/Tool 称为 Session 内 Entity，又通过独立 Repository 任意保存。
- 聚合 Repository 只服务其聚合根；跨聚合只读需求使用明确的查询端口。
- Application 负责编排跨聚合/上下文用例，Domain 负责聚合内部不变量，Mapper 只负责简单持久化。
- 架构迁移后，E1-S1、S1.1、S2 的单元、MySQL 集成、前端和人工主链路回归全部通过。
- 用户完成代码阅读并明确接受后，本 Story 才能结束并决定是否启动 E1-S3/S3.1。

## 测试矩阵

- 特征测试先锁定现有 HTTP 响应和主数据流，再移动代码。
- Aggregate 单元测试证明每次命令只加载目标小聚合；Session 不含 Turn 集合，Turn 不含 Session 或全部 Tool，Transcript 不含 Item 集合。
- Execution 测试覆盖状态单调、乱序、终态保护、事件事务、并发重试，以及两个 Session 使用相同 `turnId` 时仍按复合身份隔离。
- Transcript 测试覆盖文件替换/截断、检查点、半行、超大行、符号链接、`session_meta`、Item 继承 Path/Session 来源、Item 幂等、Turn 候选查询和变更游标；这些测试不创建 Hook 数据。
- Telemetry 测试覆盖三类信号、无效载荷、幂等、Turn 查询、变更游标和未经验证的 ID 不得产生 `EXACT`。
- Trace 测试覆盖 Hook Path/Session/Turn 到 TranscriptItem 的唯一/歧义/冲突候选、工具 Call 证据、OTel 降级、重复调度、缺源、耗时守恒和 DTO 兼容。
- 增加 ArchUnit 测试，约束分层、问题域依赖、Domain 纯净性、聚合根访问和 Repository/Query 命名。
- 使用新空 MySQL 测试 schema 验证唯一约束、事务回滚、并发、游标和完整查询链路。
- 最终执行后端 JDK 17/MySQL 全量测试、CLI 测试、前端测试与构建、浏览器 E2E，并人工回归 `IT-HOOK-TRACE-001`、`IT-HOOK-TRACE-002`。

## 当前结论

S2.1-S1 和 S2.1-S2 记录的是已实现的 Hook-first 历史基线：baseline 2 的 19 张表及 JDK 17/MySQL 完整后端测试 61 项均已验证通过，但不再继续人工验收。用户已确认 V1.0 选择 OTel + Transcript、OTel-first，原 Execution/Telemetry/Trace 所有权和 baseline 2 不能直接视为目标方案。完成规格一致性和 V3 原型门禁后，应拆分新的迁移 Story；工具级采样未完成前禁止 Tool `EXACT`。
