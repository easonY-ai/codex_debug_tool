# 技术设计

## 系统形态

产品是本地 Web 应用。Java 进程负责数据采集、解析、存储和查询；Vue 页面负责可视化分析。发布时前端静态资源嵌入 Spring Boot JAR，开发时前后端独立启动。

## Story0 持久化端口边界

Story0 的 Repository 以聚合或独立生命周期实体命名和拆分，不使用笼统的 `Store`。`RawHookEvent` 是可追溯原始证据，`NormalizationJob` 是异步调度实体，Session、Turn、Tool 是行为骨架聚合；它们分别由 `RawHookEventRepository`、`NormalizationJobRepository`、`SessionRepository`、`TurnRepository`、`ToolRepository` 表达。虽然 Hook 接收用例在同一事务内写入原始事件和任务，但该事务编排属于 Application，不改变两个 Repository 的职责边界。

## 仓库结构与实现边界

- `prd_and_design/prototype-v1` 保存已确认的 H5 设计基线，只用于还原需求、视觉和交互，不作为持续开发目录。
- `frontend` 是正式 Vue 前端工程，真实 API 接入、状态管理、自动化测试和发布构建均在此演进。
- `backend` 是 Java 服务目录，B1 已实现 JSONL 原始采集和 MyBatis 持久化；OTLP、标准化、分析和会话查询 API 在后续里程碑实施。
- 正式前端可以在后端落地前使用合成 Mock 适配层，但组件不得依赖真实账号、本机路径或私有会话样本。

参考顺序为：产品需求决定业务语义与指标口径，当前冻结的 V2 原型决定界面和交互，数据与关联模型决定 Hook/JSONL/OTel 规则，本技术设计决定工程实现。发生冲突时先修正文档并形成新原型版本，不回写已冻结的 V1 或 V2。

## 技术栈

### 后端

- Java 17。
- Spring Boot。
- Spring MVC 和 Server-Sent Events。
- MyBatis Starter、Mapper 接口和 Mapper XML。
- MySQL 8.4+、Connector/J 与 InnoDB；账号和密码仅从本地环境变量读取，切换范围见 [MySQL 切换方案](./06-mysql-migration.md)。
- Maven Wrapper 统一构建环境。

不使用 JPA、Spring Data JDBC、JdbcClient 或 MyBatis-Plus。复杂筛选和统计 SQL 写在 Mapper XML 中，避免在 Java 代码中拼接 SQL。

### 前端

- Vue 3 和 TypeScript。
- Vite 作为开发与构建工具。
- Element Plus 作为唯一通用组件库。
- Apache ECharts 6 作为唯一图表库，使用 custom series 构建瀑布图和时间泳道。
- 仅在出现跨页面共享状态需求时引入 Pinia，原型阶段默认不引入。

### 正式前端第一步：数据隔离与指标测试

- `frontend/src/data/analyzerData.ts` 定义页面数据快照和 Vue 注入接口，应用入口选择适配器。页面不直接导入共享 Mock 文件。
- `frontend/src/data/mockAdapter.ts` 为每个应用实例生成独立的合成数据副本；`mockData.ts` 仅保存演示样本。该快照是前端展示模型，不是后端 API 契约；后续 API 适配器负责字段转换和异步加载。
- `frontend/src/analysis` 保存不依赖 Vue 的指标计算。异常基线按类别和规范化操作类型隔离，先应用轮次筛选，再检查同类五样本门槛。非有限值和负耗时不进入有效样本。
- 分位数沿用 V1 的排序后线性插值：位置为 `(n - 1) × p`，在相邻样本之间插值。双阈值、贡献率与受影响轮次去重遵循 PRD。
- Vitest 覆盖异常计算边界，Vue Test Utils 覆盖筛选、点击轮次和适配器隔离。暂不引入 Pinia：当前静态快照通过依赖注入即可满足测试替换需求。

当前仍保留原型的演示限制：Trace 只有第一轮的完整样本，趋势为固定演示值，实时事件与采集状态仍含页面内演示数据。总览指标卡已按筛选后的轮次、请求和工具样本实时计算；TTFT 使用独立的请求级样本，一个轮次可以贡献多个请求，失败、取消或没有可见文本增量的请求不进入有效样本。后续需完善逐轮详情、筛选后的趋势、异步加载与错误态，再接入后端；本步骤不代表真实采集能力已经可用。

## 后端模块

- `ingestion-jsonl`：保存既有原始记录，并对 Hook 已绑定的 transcript 执行安全校验、断点读取、JSONL 解析和内容补齐；不得以目录发现结果独立创建正式会话。
- `ingestion-otlp`：接收 OTLP/HTTP JSON logs、metrics 和 traces。
- `normalization`：把不同版本的输入转换为内部稳定模型。
- `correlation`：生成跨数据源关联及其证据和可信度。
- `analysis`：计算关键路径、统计指标和诊断结论。
- `persistence`：MyBatis Mapper、事务和 MySQL 初始化。
- `api`：查询接口、采集状态接口和 SSE 推送。
- `ingestion-hooks`：接收 Codex Hook 事件并立即记录 `observedAt`，保存原始输入后再异步标准化。
- `schema-mapping`：维护 UNKNOWN 结构指纹、受限 JSONPath 映射和按指纹重新标准化任务。

模块依赖保持单向：采集模块只产生原始记录，标准化和关联失败不能阻断原始数据落库。

## 三源采集与配置

- Hooks 负责实时边界：会话、轮次、工具调用、审批、停止与中断；Hook 未安装、进程未运行、回调失败或能力未覆盖期间不重放历史。
- OTel 是 API、传输和工具性能耗时的权威来源。工具耗时精度优先级为 `OTel 精确 > Hook Pre/Post 估算 > JSONL 时间戳估算`，API 与 TTFT 不用 Hook 估算替代。
- Hook 是标准会话模型的入口。JSONL 解析任务只由已接收 Hook 的 `transcript_path` 绑定触发，用于补齐骨架内容、原始事件和 UNKNOWN；JSONL 原始行不得独立创建正式 Session、Turn 或 Tool Call。OTel 再向同一骨架补齐精确性能。
- 配置向导输出配置片段与 Hook 转发脚本，用户在 `/hooks` 中检查并信任；程序不得自动编辑用户 Codex 配置。

### Hook 乱序合并

- 不使用全局到达顺序。Turn 以 `(session_id, turn_id)` upsert，工具以 `(session_id, turn_id, tool_use_id)` upsert。
- `PostToolUse` 可以早于 `PreToolUse` 到达并创建局部记录，后到的 Pre 仅补齐缺失字段。状态只允许 `UNKNOWN → RUNNING → SUCCESS|FAILED|INTERRUPTED` 单调推进。
- 转发器收到事件即写 `observedAt`；估算耗时由同一 `tool_use_id` 的边界时间计算。时间缺失、相反或结果为负时标记无效，不进入耗时统计。

### Codex Hook 官方契约基线（2026-09-14）

契约来源为 OpenAI 官方 [Codex Hooks 文档](https://learn.chatgpt.com/docs/hooks)，并以本机 `codex-cli 0.154.0` 的稳定 `hooks` 功能和内置输入 schema 复核。官方页是发布行为基准；其链接的 Codex `main` 分支 schema 可能领先于当前发布版，因此适配器不得只按 `main` 分支生成生产配置。

- 配置支持与活动配置层相邻的 `hooks.json`，也支持 `config.toml` 内联 `[hooks]`；用户级与项目级常用位置分别是 `~/.codex/hooks.json`、`~/.codex/config.toml`、`<repo>/.codex/hooks.json` 和 `<repo>/.codex/config.toml`。同一层不应同时使用 JSON 与 TOML 表达。
- 当前事件集合为 `SessionStart`、`SessionEnd`、`PreToolUse`、`PermissionRequest`、`PostToolUse`、`PreCompact`、`PostCompact`、`UserPromptSubmit`、`SubagentStart`、`SubagentStop`、`Stop`、`Interrupt`。Hosted tools（例如 WebSearch）不走本地 function-tool Hook 路径，专用工具路径也可能选择退出，Hook 不是完整强制边界。
- 每个 command Hook 在 stdin 接收一个 JSON 对象。公共字段为必有的 `session_id:string`、`cwd:string`、`hook_event_name:string`、`model:string`，以及可空的 `transcript_path:string|null`。`transcript_path` 指向的 transcript 内容格式不是稳定 Hook 接口，仍必须按版本适配并保留 UNKNOWN。
- `turn_id:string` 是 turn-scoped 事件的 Codex 扩展：至少在 `UserPromptSubmit`、`PreToolUse`、`PermissionRequest`、`PostToolUse`、`SubagentStart`、`SubagentStop`、`Stop` 和 `Interrupt` 的事件表中声明。Session 级事件不得被接收器要求提供 `turn_id`。
- `tool_use_id:string` 只在 `PreToolUse` 与 `PostToolUse` 的官方事件字段中声明；`PermissionRequest` 有 `turn_id`、`tool_name` 与 `tool_input`，但没有获得 `tool_use_id` 保证。因此审批节点不能仅依赖 `tool_use_id` 建键，缺少公共调用 ID 时最多绑定到 Turn 或保存候选关系。
- 官方输入没有声明事件时间戳。Hook 边界时间统一使用 forwarder 收到 stdin 后立即记录的 `observedAt`，并明确标记为本地观测时间。
- handler 的 `timeout` 单位为秒；大多数 Hook 缺省 600 秒。`SessionEnd` 与 `Interrupt` 缺省 1 秒且最大 3 秒。MCP tool Hook 使用 Hook 与 MCP server 两者中较短的超时；等待 MCP elicitation 不计入该超时。
- command 与 `mcp_tool` handler 已支持；`prompt` 与 `agent` 会被解析但跳过。命令以会话 `cwd` 为工作目录。同步 Hook 会影响 agent loop；采集 forwarder 仍必须设置更短的自有网络超时并始终退出 0，避免把采集故障变成 Codex 阻塞。

版本适配器的输入契约 fixture 位于 `prd_and_design/fixtures/hooks/codex-0.154.0/`；后端实现测试应直接消费或复制后校验这些基线。升级 Codex 时必须先用官方发布行为页和目标版本 schema 更新 fixture，再允许适配器版本前移；不得把字段在某个样本中出现等同于跨版本稳定保证。

### transcript 绑定与健康

- Hook 的 `(session_id, transcript_path)` 建立会话到来源文件的文件级绑定；建立该绑定不读取或解析 JSONL 内容。
- 文件级绑定必须验证非空、规范路径位于配置根目录内、目标是可读普通文件且不是符号链接。状态为 `VALID`、`EMPTY`、`MISSING`、`UNREADABLE` 或 `OUTSIDE_ROOT_OR_SYMLINK`。
- 已知格式适配器还必须读取第一条完整 JSONL 记录，验证 `type=session_meta` 且 `$.payload.session_id` 与 Hook `session_id` 一致；扩展状态为 `SESSION_META_MISSING`、`SESSION_ID_MISMATCH` 或 `SESSION_META_UNSUPPORTED`。校验只依赖文件元数据行，不要求每条事件重复会话 ID。
- 将具体 JSONL 行关联到 Turn、消息或工具仍需解析该行；transcript 格式不稳定时，已建立的文件级绑定仍保留，未知行进入 UNKNOWN。
- 处理顺序为 `Hook 建立骨架 → transcript_path 文件级绑定 → JSONL 解析补齐内容 → OTel 关联精确耗时`。后到数据通过 upsert 补齐，不要求按此顺序到达。

### UNKNOWN 与人工映射

- 每条未知 JSON 原样保存。结构指纹由所有叶子字段的规范 JSONPath 与 JSON 值类型组成，按路径排序后计算摘要；数组元素以 `[*]` 归一化，避免仅因数组长度产生新指纹。
- 映射表达式使用 RFC 9535 的受限子集：`$`、对象成员、数组下标、`[*]`。拒绝递归下降、过滤器、函数和脚本表达式。
- 可映射字段为外层 `type`、`timestamp`、`payload.type`、`turn_id`、`call_id`、`role` 和正文。前六类标量字段必须匹配 0 或 1 个标量；正文可匹配多个字符串并按数组顺序合并。
- 示例：`$.type`、`$.timestamp`、`$.payload.type`、`$.payload.internal_chat_message_metadata_passthrough.turn_id`、`$.payload.call_id`、`$.payload.role`、`$.payload.content[*].text`、`$.payload.output[*].text`。
- 已知 JSONL 文件级元数据映射另包含 `$.payload.session_id`，仅用于 `session_meta` 与 Hook 会话校验，不作为普通事件行必填字段。
- 映射按结构指纹版本化。保存后只重新标准化该指纹的 UNKNOWN 原始记录；失败保留原始数据与错误，不阻断后续采集。

## JSONL 采集

### 后端里程碑 B1：原始数据与断点采集

首个可运行后端使用 Java 17、Spring Boot 3.5、MyBatis Starter 3.0 和 MySQL，先交付原始 JSONL 持久化、启动扫描、周期补扫、目录监听、手动增量补扫和原始事件分页查询。标准化、OTLP、关联、分析和 SSE 属于后续里程碑，未实现能力在状态接口中明确标记，不返回模拟结果。

- 采集默认关闭；仅当用户明确设置 `analyzer.jsonl.enabled=true` 和 `analyzer.jsonl.root` 后读取该根目录下的 `sessions`。配置根目录默认值为空，避免启动测试或应用时隐式读取私人会话。文档中 Codex 默认目录约定仅作为用户配置建议。
- 应用仅允许回环监听，默认端口 8080。默认连接本机 MySQL 的 `codex_analyze` 库；账号密码使用 `MYSQL_USERNAME`、`MYSQL_PASSWORD`，主机端口可用 `MYSQL_HOST`、`MYSQL_PORT` 覆盖。库表已初始化，原 SQLite 文件保留且不自动导入。
- 按单文件、分批事务写入原始行和检查点，二者一起提交或回滚。扫描串行化，MySQL 使用 InnoDB、外键与严格模式；HikariCP 管理连接，事务保持短小。
- UTF-8 按字节读取，只消费以 LF 结束的记录，兼容 CRLF；保留原始字节和原文。单行超过可配置上限时停止该文件并报告错误，检查点不越过该行，其他文件仍继续扫描。
- 文件身份、长度和检查点前缀摘要用于识别轮换、截断或重写；发现变化则建立新 generation，保留历史记录。符号链接和超出配置根目录的文件不采集。
- `GET /api/ingestion/status` 返回配置状态、扫描起止时间、错误数量、文件检查点、持久化记录数及未实现能力；本阶段整体状态是 `PARTIAL` 或 `DISABLED`，不能声称采集全部正常。
- `POST /api/ingestion/rescan` 同步执行增量补扫并返回本次文件数、新增行数和失败文件数；未启用采集时返回 409。
- `GET /api/ingestion/records?afterId=0&limit=50` 以自增 ID 游标查询原始事件，可按 `sourceId` 和 `parseStatus` 筛选；`limit` 范围为 1–200，非法参数返回 400。该接口用于验证采集与检查原始记录，不替代会话/轮次分析 API。
- 本机浏览器访问拒绝跨源请求与非本机 Host，不开放 CORS，防止外部网页触发补扫或读取正文。异常响应只含稳定错误码，不输出本机路径、SQL 或会话原文。

选择 Spring Boot 3.5 与 MyBatis Starter 3.0 是为了采用已明确兼容的组合，并继续使用 Jackson 2 生态；暂不引入 Spring Boot 4 的迁移面。兼容依据：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis Starter 版本矩阵](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。

- 采集根目录通过 `analyzer.jsonl.root` 显式配置，用户可以指向自己的 `CODEX_HOME`；未配置时不自动读取用户级目录。
- 启动时扫描 `sessions` 下的历史 JSONL 文件。
- 为每个文件记录规范路径、文件标识、已读取字节偏移和末次修改时间。
- 使用 `WatchService` 监听追加和新目录，同时周期增量补扫以弥补漏事件、系统休眠和监听器重建。
- 只处理完整换行记录；未完成的末行留到下一轮读取。
- 使用来源文件、偏移和内容摘要保证重复扫描幂等。
- 未知事件类型保留原始 JSON，不因解析失败丢弃整份文件。

以上 B1 全目录扫描能力是已实现的原始采集里程碑，不是最终产品的会话发现语义。接入 Hooks 后，正式标准化与分析只消费 Hook 已绑定的 transcript；未被 Hook 引用的扫描记录只保留在原始记录检查区，不生成分析会话。

## OTLP/HTTP 接收

提供以下仅本机可访问的 JSON 接口：

- `POST /v1/logs`
- `POST /v1/metrics`
- `POST /v1/traces`

三类数据分别保存。Trace 用于单次性能调用链；Log 用于运行事件与补充属性；Metric 用于趋势和分位数分析。Metric 不参与单次事件关联。

## 采集接口可观测性

- Hook 命令通过本机 forwarder 调用后端接收接口；Hook 本身不是 HTTP 回调。监控同时覆盖 forwarder 执行结果和后端 HTTP 接收结果。
- Hook 与 OTLP logs、traces、metrics 分别记录请求总数、2xx、非 2xx、超时、解码/校验失败、入队拒绝、队列深度、丢弃数量和最近成功时间。
- 接口处理耗时从后端收到请求开始，到完成解码、校验并成功入队为止，记录 P50/P95/P99/最大值。Hook 投递延迟另以来源时间到 `observedAt` 计算；业务工具耗时仍遵循 OTel、Hook 边界、JSONL 时间戳的精度优先级。
- 接口可用率来自本机 readiness 探测；投递成功率为 `成功接收请求数 / 实际到达请求数`。数据覆盖率是独立指标，接收端在 forwarder/exporter 未发送时不能推导覆盖完整。
- 统计窗口没有请求时状态为 `IDLE`，成功率显示未知，不用 100% 掩盖未投递故障。

## 存储设计原则

### Java/Spring 优先的事务管理

- 事务边界、提交和回滚统一使用 Spring `@Transactional` 或 `TransactionTemplate`；Java 服务层负责业务流程、校验与重试。MyBatis 操作参与同一 Spring 管理的数据库事务。
- 尽量避免使用 MySQL 触发器、存储过程、存储函数或数据库定时事件承载业务逻辑和事务编排；例外需先记录 Java/Spring 方案不足、权限要求及维护成本。
- MySQL 继续负责实际事务原子性、主外键、唯一约束和索引，保障完整性与并发幂等；应用层校验不能替代这些约束。
- 集成测试通过 Java 层注入写入异常，验证真实测试库内已写入记录和检查点一起回滚，不依赖触发器制造错误，也不要求提升全局权限或修改 MySQL 全局配置。

MySQL 至少保存以下逻辑实体：

- 数据源与读取检查点。
- JSONL 原始事件。
- OTel 原始日志、指标和 Span。
- 标准化会话、Turn、Agent Event 和 Performance Span。
- 跨数据源 Alignment 及其关联证据。
- 诊断结果。

原始数据与标准化数据分离，解析器升级后可以重新标准化。数据库使用 InnoDB，并对实际查询涉及的会话时间、Turn、Call ID、Trace/Span ID、事件时间和工具类型建立索引。新增索引前使用代表性查询和 `EXPLAIN` 验证收益。

## API

- `GET /api/sessions`：分页查询、筛选和慢会话排行。
- `GET /api/sessions/{id}/analysis`：返回行为事件、性能 Span、关联关系和诊断结论。
- `GET /api/overview`：趋势、P50/P95 和分类统计。
- `GET /api/ingestion/status`：数据源状态、读取进度和未关联数量。
- `POST /api/ingestion/rescan`：触发增量补扫，不删除或覆盖原始数据。
- `GET /api/events`：通过 SSE 推送新增会话、事件和 Span。

分析详情的稳定返回结构包含：

```text
session
agentEvents[]
performanceSpans[]
alignments[]
diagnoses[]
aggregates
```

所有时间使用 Unix 毫秒值，并同时返回数据来源、精度和关联等级。

## Story0 领域化架构约束

- 后端按问题子域组织 `interfaces`、`application`、`domain`、`infrastructure` 四层；每层内部继续按 `hook-ingestion`、`hook-normalization`、`trace-query` 等业务子域拆解，不按技术类型集中目录。
- `interfaces` 是入站适配层：HTTP Controller、定时调度器和消息入口只转换协议并调用 Application 用例。`HookNormalizationScheduler` 只负责 `@Scheduled` 调度和调用 `NormalizeHookEventUseCase`，不解析事件、不推进状态、不直接访问 Mapper。
- `application` 编排用例、事务和端口；`domain` 持有聚合根、实体、值对象和领域规则；`infrastructure` 提供 Spring、Jackson、MyBatis 和数据库适配实现。
- REST 输入输出必须使用明确 DTO。普通业务接口不得以 `JsonNode`、`Map`、`Object` 或裸 `byte[]` 作为业务输入输出。
- Raw Event 是原始证据保留的特例：接收边界可以接收并保存专门的 Raw Event 类型；已知事件在归一化阶段转换为结构化领域事件，未知事件显式标记 UNKNOWN 并保留原始证据。
- 简单标识符和时间值第一轮不机械封装为 Value Object；只有存在稳定业务不变量或跨子域行为时才引入。
- Mapper 仅执行简单读写和数据库约束配合；状态推进、跨实体规则、跨源关联、证据和指标计算由 Java Application/Domain 层实现。

## 正式实施契约

### Hook 安装器与 forwarder

- Codex Hook 是本地命令回调，不把 Hook 本身描述为 HTTP webhook。配置向导生成版本化 Hook 配置和可执行 forwarder，用户必须在 Codex `/hooks` 中检查并信任；应用不自动修改用户配置。
- Codex Hook 的原始 stdin JSON 不做字段裁剪，forwarder 立即生成 `delivery_id`（每次命令调用一个 UUID）和 `observed_at`，封装为 `schema_version=1` 后 POST 到 `http://127.0.0.1:8080/api/ingestion/hooks`。
- 请求头为 `Content-Type: application/json` 和 `X-Trace-Lens-Forwarder-Version`。首版只监听 `127.0.0.1`，不要求 Bearer Token，也不允许跨域调用；未来支持非回环监听或远程采集时必须另行设计认证。
- 请求体固定为 `schemaVersion`、`deliveryId`、`observedAt`、`forwarderVersion`、`rawEvent`。后端不得要求 forwarder 理解具体 Hook 版本。
- 连接超时 250ms、单次请求总超时 1s；仅对连接失败、超时或 5xx 立即重试一次，退避 50ms。完成本次 Hook 命令后不写磁盘队列、不做延迟补发或历史重放。
- 新事件完成基本校验并与标准化任务一起入队后返回 `202 { deliveryId, status: "ACCEPTED" }`；相同 `deliveryId` 返回 `200 { deliveryId, status: "DUPLICATE" }`；格式错误返回 400，请求过大返回 413，接收队列或原始落库不可用返回 503。JSONL 解析、关联和业务表写入均在响应后异步执行，其异常不得改变已经返回的 Hook 接收结果。
- forwarder 不等待 Java 后台解析、关联或业务写库。无论收到 2xx、4xx、5xx，还是遇到连接失败、超时、非法 stdin 或本地脚本错误，均只写脱敏本机诊断并退出 0，确保采集链路不会阻断 Codex；差异作为 forwarder 结果类别进入 Hook 链路健康统计，不依赖非零进程退出码表达。
- 官方 Hook 配置的具体序列化格式由 `codex_hook_config_version` 适配器封装；安装器必须用当前 Codex 版本的官方 schema 生成并在临时目录执行自检。技术设计不把未经验证的示意 TOML 当作正式格式。
- 首个已验证配置版本可生成 `hooks.json` 或内联 TOML；正式 fixture 使用 `hooks.json`，结构为顶层可选 `description` 与 `hooks`，事件名映射到 matcher group 数组，每个 group 包含可选 `matcher` 和 `hooks` handler 数组。handler 至少包含 `type=command` 与 `command`，可选 `timeout`、`statusMessage`、`additionalContextLimit`、`commandWindows` 和 `async`。

### Hook 接收与标准化 API

- `POST /api/ingestion/hooks` 仅绑定回环地址，拒绝跨域调用，先验证 Content-Type、大小上限和 JSON，再在单事务内插入 `raw_hook_event` 与 `normalization_job`，不得同步解析 transcript、执行关联、写入业务骨架或等待 OTel。
- 单请求原始事件上限 1MiB；超过返回 413。正文、工具参数和结果不写普通日志。
- `GET /api/ingestion/hooks/status` 返回监听、最近成功、到达请求数、2xx/4xx/5xx、超时、forwarder 结果类别、处理耗时分位数、队列、丢弃、乱序、重复和局部事件数。
- `GET /api/ingestion/transcripts/status` 返回路径安全、session_meta 校验、内容补齐和 UNKNOWN 汇总。
- `GET /api/ingestion/otel/status` 按 logs、traces、metrics 返回接口可用、投递成功率、处理耗时、失败、队列和丢弃；无请求窗口成功率为 null，状态为 `IDLE`。
- `GET /api/unknown-fingerprints` 分页查询结构指纹；`GET /api/unknown-fingerprints/{id}` 返回合成/脱敏预览所需原始样本；`PUT /api/unknown-fingerprints/{id}/mapping` 校验并版本化保存映射；`POST /api/unknown-fingerprints/{id}/renormalize` 只重处理该指纹。

### MySQL 逻辑表

- `raw_hook_event(id, delivery_id, observed_at, received_at, forwarder_version, raw_json, parse_status, error_code)`；`delivery_id` 唯一。
- `hook_session(session_id, transcript_path, started_at, ended_at, state, last_observed_at, version)`；主键 `session_id`。
- `hook_turn(session_id, turn_id, started_at, ended_at, state, version)`；唯一键 `(session_id, turn_id)`。
- `hook_tool_call(session_id, turn_id, tool_use_id, tool_name, pre_observed_at, post_observed_at, state, estimated_duration_ms, duration_valid, version)`；唯一键 `(session_id, turn_id, tool_use_id)`。
- `transcript_binding(id, session_id, configured_path, canonical_path, path_status, source_file_id, session_meta_record_id, jsonl_session_id, session_check_status, adapter_version, checked_at)`；`session_id` 唯一。
- `jsonl_supplement(id, hook_node_type, hook_node_id, raw_record_id, content_kind, call_id, mapping_level, evidence_json, adapter_version)`；唯一键 `(hook_node_type, hook_node_id, raw_record_id, content_kind)`。
- `unknown_fingerprint(id, fingerprint_sha256, canonical_shape, source_kind, first_seen_at, last_seen_at, occurrence_count, mapping_status)`；`fingerprint_sha256` 唯一。
- `unknown_mapping(id, fingerprint_id, version, mapping_json, status, validation_error, created_at)`；唯一键 `(fingerprint_id, version)`。
- `raw_otel_object(id, signal_type, trace_id, span_id, event_time, received_at, raw_json, parse_status)`；按 `(signal_type, trace_id, span_id)` 建条件唯一约束，无这些 ID 时用接收批次与对象序号幂等。
- `performance_alignment(id, hook_node_type, hook_node_id, otel_object_id, level, evidence_json, algorithm_version, time_delta_ms)`。

原始表只追加；标准化表使用乐观 `version` upsert。完整 SQL、索引和迁移编号在实现对应里程碑时落入 `backend/src/main/resources/db/migration`，但不得改变上述语义和唯一键。

### 幂等、乱序与事务

- 原始 Hook 接收以 `delivery_id` 幂等；JSONL 延续 `(source_id, generation, byte_offset)`；OTel 优先使用 Trace/Span/Event 标识，缺失时使用批次 ID 与对象序号。
- 标准化 worker 每次先读取原始记录，再按 `(session_id, turn_id)` 或 `(session_id, turn_id, tool_use_id)` upsert。Post 先到可建立局部工具记录，Pre 后到只补字段；终态不回退到 RUNNING。
- Hook、JSONL、OTel 各自在独立短事务中原样落库，任何解析或关联失败不得回滚另一来源。标准化、transcript 绑定、内容补齐和性能关联分别使用可重试事务。
- transcript 必须先通过规范路径安全检查，再读取第一条完整 JSONL；仅当 `type=session_meta` 且 `$.payload.session_id == Hook.session_id` 时允许内容补齐。ID 不一致保留两侧证据并停止挂接。
- 工具精确补齐要求当前适配器已验证且 `Hook.tool_use_id == JSONL.call_id`；值不同最多为 `INFERRED`。同 Turn、同工具类型、时间重叠可形成候选，多个候选不得自动提升。
- 工具耗时精度为 `OTel 精确 > Hook Pre/Post 估算 > JSONL 时间戳估算`；TTFT 仅来自 OTel，缺失显示未知。

### UNKNOWN 映射执行

- 用户操作固定为：选择结构指纹，查看原始样本，为目标字段填写 JSONPath，查看实际匹配值、数量和类型校验，保存映射，再只重处理该指纹的记录。
- JSONPath 求值器只实现 `$`、对象成员、数组下标、`[*]`。每个表达式必须完整消费，拒绝递归下降、过滤器、函数、脚本、联合选择和切片。
- `type`、`timestamp`、`payload.type`、`turn_id`、`call_id`、`role` 匹配数必须为 0 或 1，匹配值必须是标量；正文可以匹配多个字符串并按数组顺序连接。预览必须执行用户当前输入，不能展示硬编码结果。
- 保存前返回每个字段的 `matchCount`、`valueType`、`preview` 和错误；存在语法、基数或类型错误时不得保存。映射保存与重新标准化分开执行，失败不覆盖上一有效版本。

### 正式实施里程碑

1. **B2 Hook 原始接收**：迁移、仅回环且禁止跨域的接收 API、forwarder、幂等和链路健康；用重复投递、非法输入、请求过大、后台异常、超时和无请求窗口测试验收，并验证所有 forwarder 结果均不以非零退出码影响 Codex。
2. **B3 Hook 骨架**：Session/Turn/Tool upsert、单调状态和 Hook 估算耗时；Post 先到、重复终态和负耗时测试必须通过。
3. **B4 transcript 内容补齐**：路径安全、session_meta 校验、已知适配器、UNKNOWN 指纹和人工映射；ID 不一致不得挂接。
4. **B5 OTLP 与性能关联**：三类 OTLP 接口、原始存储、Hook→OTel Alignment、TTFT 与精度优先级。
5. **B6 查询与 SSE**：会话列表、Trace、实时事件、完整度、采集状态和 UNKNOWN API；缺失来源必须返回明确 null/status。
6. **F2 正式前端迁移**：按已确认 V2 实现总览、实时、Trace、采集状态与映射交互，移除页面内 Mock 依赖并增加组件/E2E 测试。
7. 每一里程碑完成后运行后端测试、前端测试、构建、迁移回滚测试和敏感信息扫描；未完成能力继续返回 `NOT_IMPLEMENTED`，不得伪造正常状态。

## 配置与交付

- 服务默认只绑定 `127.0.0.1`。
- 数据库和库表由用户预先创建；本机地址、端口通过 `MYSQL_HOST`、`MYSQL_PORT` 配置，账号和密码通过 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 配置。
- 设置页只检测 OTel 配置状态并生成可复制配置，不自动修改用户文件。
- 开发模式分别启动 Spring Boot 与 Vite，由 Vite 代理后端接口。
- 发布构建先生成前端静态资源，再打入可执行 JAR。
- 提供单条打包命令，以及 macOS/Linux 启动脚本。

## 测试策略

- JUnit 5 和 Spring Boot Test 覆盖解析、存储、关联和 API。
- 单元测试不依赖外部数据库。MyBatis 集成测试通过独立 Maven profile 在临时 MySQL 实例上运行，使用合成数据，不复用实际采集库。
- Vitest 和 Vue Test Utils 覆盖前端状态与组件。
- Playwright 覆盖总览到 Trace 详情的核心用户路径。
- Fixture 全部为合成数据，不读取开发者真实 Codex 目录。
