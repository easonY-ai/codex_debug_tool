# Story1.1：本地运行日志

## Story 信息

- 编号：E1-S1.1。
- 名称：本地运行日志。
- 优先级：P0，强关联 E1-S1。
- 状态：开发完成；用户于 2026-09-16 完成 `IT-LOG-001`、代码阅读并明确接受。
- 启动记录：用户于 2026-09-16 明确启动。
- 前置：E1-S1 已验收完成；不改变其 Hook→Trace 功能语义。

## 用户价值

作为本机 Trace Lens 使用者，我需要在不暴露 Codex 对话或凭据的前提下，查看 CLI forwarder 与后端接收/异步处理的运行结果，以诊断“为什么没有采集到数据”，同时不让诊断能力阻断 Codex 或业务请求。

## 范围与非目标

包含：

- Python CLI forwarder 的结构化本地文件日志；
- Java 后端 Hook 接收和 Hook 归一化失败的结构化本地文件日志；
- 按自然日轮转、七天上限和启动清理；
- 单元测试和集成测试可使用临时日志根目录验证。

不包含：

- 前端日志、浏览器遥测或把日志上传到远程服务；
- 持久化运行指标的高可用改造（E2-S1）；
- HTTP API、页面或数据库表中的日志查询；
- 将 Hook 原文、用户内容或凭据写入日志。

## 日志契约

默认目录及文件：

```text
~/.my_logs/codex_analyze/
  cli/trace-lens-cli.log
  backend/trace-lens-backend.log
```

- `TRACE_LENS_LOG_ROOT` 仅用于本机运维或自动化测试覆盖日志根目录；未设置时必须使用上述默认根目录。
- 每行是一个独立、可机器解析的记录，字段为 `timestamp`、`level`、`component`、`event`、`outcome`，可选字段为 `httpStatus`、`attempt`、`durationMs`、`errorCategory`。接口诊断另记录白名单字段和稳定 Hash；字段值只能来自受控枚举、数值或 SHA-256 摘要。
- 日志按本地自然日轮转；当前日和此前六个自然日合计最多七天。每次 CLI 启动或后端启动时都清理更早的同组件日志。删除范围仅限对应组件目录与该组件文件名前缀。
- CLI 事件至少覆盖 `hook_forward_completed` 与 `hook_forward_rejected_input`；结果使用 `accepted`、`rejected`、`unavailable`、`invalid_input`、`internal_error` 等稳定类别。
- 所有具备业务职责的公开入口均记录调用入参摘要、返回值摘要、耗时、结果或异常类别：包括 Controller HTTP 接口、Scheduler 调度入口、Application Use Case 公开方法和 Domain Service 的公开业务能力；不包括构造器、getter/setter、record accessor、Repository、Mapper 与实体内部状态方法。Hook HTTP 请求记录 `schemaVersion`、`forwarderVersion`、`hookEventName`、`bodyLength`、`bodySha256` 和存在时的 `sessionIdHash`、`turnIdHash`、`toolUseIdHash`、`deliveryIdHash`；响应记录 `httpStatus`、`result` 与 `durationMs`。异常只记录稳定错误类别，不能直接格式化异常消息或堆栈。
- 日志初始化、写入、轮转或清理失败均为诊断降级：CLI 继续遵守“总是退出 0”，后端继续处理 HTTP/异步任务，不得改变业务响应或事务结果。

## 脱敏规则

严禁出现在 CLI 或后端普通日志中的字段包括：原始 Hook JSON、HTTP body、用户问题、工具参数/结果、模型输出、完整的 `session_id`、`turn_id`、`tool_use_id`、`delivery_id`、`transcript_path`、Cookie、Authorization、数据库 URL/账号/密码和异常原始 message。对应标识符仅允许以 SHA-256 摘要形式出现。测试必须用明显的虚构敏感字符串证明其未写入，并证明相同标识符产生稳定摘要。

## 一致性门禁

| 检查面 | 结论 | 处理 |
| --- | --- | --- |
| PRD 与 V2 原型 | 当前产品要求本机数据不外传；前端日志不在已确认范围 | 不修改冻结原型或前端 |
| S1 Hook 契约 | forwarder 失败必须退出 0，后端接收不得记录原始 body | 日志只能作尽力而为诊断 |
| DDD 边界 | 运行日志不是领域状态；入口适配器/用例可记录结果，Mapper 不承担日志规则 | 不引入 SQL 日志或业务日志表 |
| 测试独立性 | 单元测试不得写用户目录或依赖真实 Codex/MySQL | 以临时根目录和虚构数据验证 |

不存在需要变更 PRD 或创建 V3 原型的阻塞项。

## 测试用例

| 编号 | 层级 | 场景 | 预期 |
| --- | --- | --- | --- |
| UT-LOG-CLI-001 | CLI 单元 | accepted、5xx 重试成功、invalid input | 产生受控字段和结果类别；不含虚构原文/ID/凭据；始终返回 0 |
| UT-LOG-CLI-002 | CLI 单元 | 目录不可写、日志 handler 抛错 | forwarder 仍尝试投递并返回 0 |
| UT-LOG-CLI-003 | CLI 单元 | 当前日与过期归档文件 | 仅删除超过七天范围的同前缀文件 |
| UT-LOG-BE-001 | 后端单元 | 日志配置路径、滚动策略、受控字段、业务入口切面 | 默认目录正确；可在测试中覆盖为临时目录；不启用 SQL/body 日志；业务入口仅记录类型、大小与稳定摘要 |
| IT-LOG-BE-001 | 后端集成 | accepted、duplicate、非法 Hook、归一化失败 | 请求/响应日志包含白名单入参、Hash、状态和业务结果；不含虚构 body、完整 ID 或数据库凭据；HTTP/任务原有语义不变 |
| IT-LOG-BE-002 | 后端集成 | 启动时存在八天前日志 | 仅清理本组件过期前缀文件；保留无关文件 |
| IT-LOG-001 | 人工集成 | 真实 Hook 主链路和后端运行 | 两个组件目录均出现当日日志；无真实会话、凭据或原文进入 Git 或日志检查记录 |

## 自动测试记录

| 日期 | 范围 | 结果 | 证据 |
| --- | --- | --- | --- |
| 2026-09-16 | CLI：日志结构、敏感原文排除、轮转清理及既有 forwarder 用例 | 通过 | `uv run --project cli python -m unittest discover -s cli/tests -v`，6 项通过 |
| 2026-09-16 | 后端：接收事件脱敏日志、接口请求/响应摘要、日志目录/轮转配置 | 通过 | JDK 17 定向 Maven 测试，`AcceptHookEventUseCaseTest`、`HookHttpAuditLoggerTest` 与 `RuntimeLoggingConfigurationTest` 共 4 项通过 |
| 2026-09-16 | 后端：统一业务入口审计摘要 | 通过 | 增加 Spring AOP 切面后，JDK 17 定向 Maven 测试：`BusinessOperationAuditAspectTest`、`HookHttpAuditLoggerTest`、`AcceptHookEventUseCaseTest`、`RuntimeLoggingConfigurationTest` 共 5 项通过 |
| 2026-09-16 | 后端全量 MySQL 回归 | 未启动测试 | Spring 上下文加载到 `HookIngestionController` 时发现 `target/classes` 为 Java 21 class file version 65，而命令使用 JDK 17（仅支持 version 61）；22 个集成用例因此被失败阈值跳过。该构建产物污染与 S1.1 断言无关，需在稳定 JDK 17 构建产物后重跑 |
| 2026-09-16 | CLI 全量与后端 JDK 17/MySQL 全量回归 | 通过 | CLI 6 项通过；后端 36 项通过，0 failures/errors；额外检查确认无 Repository 业务审计日志、无 Raw Hook 反序列化器 CGLIB 警告。此前 Java 21 构建产物污染记录已由本次干净回归覆盖 |
| 2026-09-16 | `IT-LOG-001` 与 S1.1 人工门禁 | 通过 | 用户确认两个组件的当日日志、真实 Hook 不阻断与敏感信息边界符合预期，完成代码阅读并明确接受 S1.1 |

## Definition of Done

- CLI 与后端均按契约产生本地结构化日志并完成七天清理。
- 全部 S1.1 单元/集成测试通过，且不依赖真实 Codex 数据或用户日志目录。
- 用户手工完成 `IT-LOG-001`，确认 Hook 不被日志阻断。
- 日志中无原始内容、标识符或凭据；仓库也未提交日志文件。
- 用户完成代码阅读并明确接受 S1.1，才可启动 S2。
