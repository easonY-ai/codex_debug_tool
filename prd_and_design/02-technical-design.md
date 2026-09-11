# 技术设计

## 系统形态

产品是本地 Web 应用。Java 进程负责数据采集、解析、存储和查询；Vue 页面负责可视化分析。发布时前端静态资源嵌入 Spring Boot JAR，开发时前后端独立启动。

## 仓库结构与实现边界

- `prd_and_design/prototype-v1` 保存已确认的 H5 设计基线，只用于还原需求、视觉和交互，不作为持续开发目录。
- `frontend` 是正式 Vue 前端工程，真实 API 接入、状态管理、自动化测试和发布构建均在此演进。
- `backend` 是后续 Java 服务目录，创建后负责 JSONL/OTLP 采集、MyBatis 持久化、分析和查询 API。
- 正式前端可以在后端落地前使用合成 Mock 适配层，但组件不得依赖真实账号、本机路径或私有会话样本。

参考顺序为：产品需求决定业务语义与指标口径，冻结原型决定界面和交互，数据与关联模型决定 JSONL/OTel 规则，本技术设计决定工程实现。发生冲突时先修正文档并形成新原型版本，不回写已冻结的 V1。

## 技术栈

### 后端

- Java 21。
- Spring Boot。
- Spring MVC 和 Server-Sent Events。
- MyBatis Starter、Mapper 接口和 Mapper XML。
- SQLite JDBC，数据库使用 WAL 模式。
- Maven Wrapper 统一构建环境。

不使用 JPA、Spring Data JDBC、JdbcClient 或 MyBatis-Plus。复杂筛选和统计 SQL 写在 Mapper XML 中，避免在 Java 代码中拼接 SQL。

### 前端

- Vue 3 和 TypeScript。
- Vite 作为开发与构建工具。
- Element Plus 作为唯一通用组件库。
- Apache ECharts 6 作为唯一图表库，使用 custom series 构建瀑布图和时间泳道。
- 仅在出现跨页面共享状态需求时引入 Pinia，原型阶段默认不引入。

## 后端模块

- `ingestion-jsonl`：发现文件、断点读取、JSONL 解析和原始事件保存。
- `ingestion-otlp`：接收 OTLP/HTTP JSON logs、metrics 和 traces。
- `normalization`：把不同版本的输入转换为内部稳定模型。
- `correlation`：生成跨数据源关联及其证据和可信度。
- `analysis`：计算关键路径、统计指标和诊断结论。
- `persistence`：MyBatis Mapper、事务和 SQLite 初始化。
- `api`：查询接口、采集状态接口和 SSE 推送。

模块依赖保持单向：采集模块只产生原始记录，标准化和关联失败不能阻断原始数据落库。

## JSONL 采集

- `CODEX_HOME` 为可配置根目录，默认遵循 Codex 的用户级数据目录约定。
- 启动时扫描 `sessions` 下的历史 JSONL 文件。
- 为每个文件记录规范路径、文件标识、已读取字节偏移和末次修改时间。
- 使用 `WatchService` 监听追加和新目录，同时周期增量补扫以弥补漏事件、系统休眠和监听器重建。
- 只处理完整换行记录；未完成的末行留到下一轮读取。
- 使用来源文件、偏移和内容摘要保证重复扫描幂等。
- 未知事件类型保留原始 JSON，不因解析失败丢弃整份文件。

## OTLP/HTTP 接收

提供以下仅本机可访问的 JSON 接口：

- `POST /v1/logs`
- `POST /v1/metrics`
- `POST /v1/traces`

三类数据分别保存。Trace 用于单次性能调用链；Log 用于运行事件与补充属性；Metric 用于趋势和分位数分析。Metric 不参与单次事件关联。

## 存储设计原则

SQLite 至少保存以下逻辑实体：

- 数据源与读取检查点。
- JSONL 原始事件。
- OTel 原始日志、指标和 Span。
- 标准化会话、Turn、Agent Event 和 Performance Span。
- 跨数据源 Alignment 及其关联证据。
- 诊断结果。

原始数据与标准化数据分离，解析器升级后可以重新标准化。数据库开启 WAL，并对实际查询涉及的会话时间、Turn、Call ID、Trace/Span ID、事件时间和工具类型建立索引。新增索引前使用代表性查询和 `EXPLAIN QUERY PLAN` 验证收益。

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

## 配置与交付

- 服务默认只绑定 `127.0.0.1`。
- 数据库默认位于应用自己的本机数据目录，可通过启动参数覆盖。
- 设置页只检测 OTel 配置状态并生成可复制配置，不自动修改用户文件。
- 开发模式分别启动 Spring Boot 与 Vite，由 Vite 代理后端接口。
- 发布构建先生成前端静态资源，再打入可执行 JAR。
- 提供单条打包命令，以及 macOS/Linux 启动脚本。

## 测试策略

- JUnit 5 和 Spring Boot Test 覆盖解析、存储、关联和 API。
- MyBatis 集成测试使用临时 SQLite 数据库。
- Vitest 和 Vue Test Utils 覆盖前端状态与组件。
- Playwright 覆盖总览到 Trace 详情的核心用户路径。
- Fixture 全部为合成数据，不读取开发者真实 Codex 目录。
