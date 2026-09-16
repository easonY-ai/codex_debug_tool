# Story0：领域化架构升级

## Story 信息

- 编号：E0-S0。
- 名称：领域化架构升级。
- 优先级：P0，Story1 后续集成测试的前置门禁。
- 状态：集成测试中；Hook 主链路自动回归已通过，等待用户重新执行受影响的人工主链路。
- 范围：第一轮仅重构 Story1 的 Hook→Trace 主链路。

## 背景与问题

1. 主链路大量使用 `JsonNode`、`Map` 和 `byte[]` 表示业务输入，静态正确性和可读性较弱。
2. Hook 接收、归一化和状态推进集中在流程类中，Session/Turn/Tool 的职责不清晰。
3. `upsertHookSession`、`upsertHookTurn`、`upsertHookTool` 以及 `alignExactOtelTools` 在 SQL 中承载状态迁移或跨源业务规则。
4. `HookNormalizationWorker` 同时承担调度、解析、归一化和持久化编排，边界不清晰。

## 架构约束

后端采用按问题子域拆解的四层结构：

```text
interfaces/
  hook-ingestion/
  hook-normalization/
  trace-query/
application/
  hook-ingestion/
  hook-normalization/
  trace-query/
domain/
  hook-ingestion/
  hook-normalization/
  trace-query/
infrastructure/
  hook-ingestion/
  hook-normalization/
  trace-query/
```

不采用全局 `entity/`、`service/`、`enum/`、`mapper/` 技术分类作为主要领域结构。

### 调度器边界

`HookNormalizationWorker` 重命名为 `HookNormalizationScheduler`，放入 `interfaces/hook-normalization`：

```text
HookNormalizationScheduler (@Scheduled)
  → NormalizeHookEventUseCase
  → HookEventParser 端口
  → Session/Turn/Tool 领域模型
  → RawHookEventRepository / NormalizationJobRepository / SessionRepository / TurnRepository / ToolRepository
  → MyBatis Repository 实现
```

调度器只触发用例，不解析 JSON、不推进领域状态、不直接访问 Mapper。

### REST 与 Raw Event

- REST 默认使用明确业务 DTO。
- Hook envelope 使用结构化 `HookEnvelopeRequest`。
- Raw Event 是保留未知/未支持输入的特殊原始证据类型，用于简化 CLI 转发。
- 已知 Hook 事件在归一化阶段转换为结构化事件对象；未知事件转换为 `UnknownHookEvent` 并保留原始证据。
- `JsonNode` 仅允许出现在 Jackson 适配边界，不进入 Domain/Application 公共接口。
- `Map<String,Object>`、`Object` 和裸 `byte[]` 不作为普通 REST 业务输入输出。

### 领域模型职责

- 聚合根：维护 Hook 会话或一次投递的生命周期与不变量。
- Entity：维护 Turn、Tool Call 等具有身份和状态的业务对象。
- Value Object：第一轮不为简单 ID、时间和版本值机械包装；只有存在稳定业务行为时才引入。
- 领域服务：承载跨实体状态规则和跨源关联规则，并注释职责、不变量和对外接口。

同一子域内任一聚合的状态更新均由对应领域服务封装：通过 Domain 定义的 Repository 读取当前聚合、调用聚合的 `apply` 执行状态规则并保存结果。Story0 中分别为 `SessionLifecycleService`、`TurnLifecycleService` 和 `ToolLifecycleService`；`NormalizeHookEventUseCase` 只调用这些能力并编排任务状态。

Session/Turn/Tool 的状态推进、终态保护、乱序处理和缺失字段规则必须在 Java 中实现。

### 持久化边界

Repository/Mapper 仅提供查询、插入、更新和数据库约束配合。Repository 必须按聚合根或独立生命周期实体命名和拆分：`RawHookEvent` 与 `NormalizationJob` 即使在同一接收事务中创建，也不共享笼统的 `Store`；Session、Turn、Tool 也各有 Repository。Use Case 组合 Repository 并定义事务。数据库继续负责主键、唯一键、外键、版本和并发完整性；业务状态迁移、跨实体规则、关联等级、证据构建和指标计算由 Java Application/Domain 层完成。

并发创建同一聚合时，Repository 不得捕获唯一键冲突后拿旧快照直接更新。唯一键冲突必须穿透并回滚当前事务；Application Use Case 在新事务中重新读取聚合、重新调用领域状态迁移并限次重试，避免乱序事件覆盖终态或已补齐的时间边界。

`alignExactOtelTools` 的 OTel 关联算法迁移留给后续 S3.1，不在 Story0 实施。

## Story0 实施范围

包含：

- Hook 接收 DTO 和结构化返回 DTO；
- Hook 原始证据类型与已知/未知事件转换；
- Hook 接收 Application 用例；
- `HookNormalizationScheduler` 与归一化 Application 用例拆分；
- Session/Turn/Tool 领域模型与状态规则；
- Repository 端口及 Story1 所需 MyBatis 适配器；
- Story1 查询 DTO，保持现有接口字段语义兼容。

不包含：

- transcript 全量结构化；
- OTel 全量结构化和 `alignExactOtelTools` 迁移；
- 前端日志；
- 运行指标高可用化；
- 数据库表的大规模迁移；
- 冻结原型修改。

## 测试与验收

### 当前执行记录（2026-09-15）

- 领域生命周期单元测试：`HookLifecycleTest` 3 项通过。
- Hook 接收与归一化 MySQL 集成回归：显式 DTO、413 超限保护、幂等入队、Pre/Post 乱序、终态不可回退、负耗时和查询回归通过。
- 后端全量自动回归：31 tests，0 failures/errors。
- 2026-09-16：用户重新人工执行 `IT-HOOK-TRACE-001` 步骤 1–10，全部通过；此前架构升级前的通过记录未被用作本次回归替代。

- 领域对象单元测试不依赖 Spring、MyBatis、Jackson、网络或真实数据库。
- DTO 协议测试覆盖合法、缺字段、未知事件和超限输入。
- Application 测试覆盖幂等、事务边界和任务入队。
- 归一化测试覆盖 SessionStart 无 `turn_id`、工具事件缺失 `turn_id`、Pre/Post 乱序和终态不可回退。
- MySQL 集成测试覆盖 Repository 映射、主键回填、唯一键和事务回滚。
- 后端完整测试、前端单元测试和构建必须通过。
- 重新人工执行 Story1 `IT-HOOK-TRACE-001` 步骤 1–10，确认真实 Hook→Trace 行为不变。
- Story0 验收通过后，才解除 Story1 后续集成测试门禁。

## Definition of Done

- 本文档及 `AGENTS.md`、技术设计、Epic/Story 台账和 README 索引已同步。
- Story0 范围内代码完成并通过单元/集成测试。
- Story1 主链路步骤 1–10 回归通过。
- 用户人工确认 Story0 完成后，才开始 Story1 后续集成测试。
