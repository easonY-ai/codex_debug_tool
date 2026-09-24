# MySQL 切换方案

## 已确认需求

- 2026-09-14：用户要求数据库改用 MySQL，账号和密码放在本地环境变量中。
- 延续 PRD 的本机数据边界，MySQL 部署在本机，Web 服务继续仅监听回环地址。
- 账号使用 `MYSQL_USERNAME`，密码使用 `MYSQL_PASSWORD`；没有默认账号或密码，缺失时应明确报配置错误，不回退到 SQLite。
- 主机和端口使用 `MYSQL_HOST`、`MYSQL_PORT`，默认值为 `127.0.0.1`、`3306`；数据库名称固定为已初始化的 `codex_analyze`。
- 本地环境变量由启动进程继承；不读取或打印真实凭据，不把密码拼接到 JDBC URL、命令行或日志。仓库只提供变量名与无真实凭据的配置说明。
- 配置规则：所有运行配置（包括环境变量占位符、JDBC URL、数据库名称、连接池、超时、编码、排序规则和 SQL 模式）统一写入 `backend/src/main/resources/application.yml`；Java 配置类只绑定并校验这些 Spring 配置，不得手工读取 `System.getenv()` 或在代码中硬编码连接池参数。账号密码仍只通过 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 注入，不写入 YAML 明文。

本文件的已确认需求替代旧文档中的 SQLite 选型。使用新的空 MySQL 数据库，不导入、修改或删除旧 SQLite 文件。

## 一致性门禁

- **历史结论**：数据库选型变更本身不改变当时的 Hook-first 数据语义；E1-S2.2 后续独立把目标架构升级为 OTel + Transcript、OTel-first，数据库 baseline 必须在代码迁移前按新所有权重新评审并递增，不能沿用 baseline 2 名称静默改表。
- **已明确**：现有 schema、Mapper、迁移器、启动脚本及测试依赖 SQLite，必须整体调整，不能仅替换 JDBC 驱动。
- **已确认**：用户选择新的空 MySQL 数据库并保留原 SQLite 文件；不实施历史数据迁移。没有待确认的阻塞项。
- **原型差异**：冻结 V2 的采集健康项写有 SQLite，属于旧存储选型；正式前端当前没有此文案。本次仅调整存储和测试配置，不改页面结构或交互，V1/V2 保持只读。后续若修改可见产品交互，按仓库规则创建 V3。

## 实施设计

- 使用 MySQL 8.4+、MySQL Connector/J 与 HikariCP，MyBatis 继续负责业务 SQL。库表已由部署方初始化，应用不执行 schema.sql 或迁移器。
- 使用 InnoDB，保留原始行与检查点同事务提交、唯一键幂等和 Hook 状态单调推进。
- Unix 毫秒时间、字节偏移和记录 ID 使用 BIGINT；正文与原始字节使用足够容量的文本和二进制列，覆盖既有最大行长度配置。
- ID 与路径比较保持大小写敏感，避免 MySQL 默认不区分大小写的排序规则合并不同事件。长路径和外部标识保留完整 LONGTEXT，使用生成的 SHA-256 二进制列建立唯一索引，复合标识分别计算摘要，避免前缀截断和分隔符歧义；查询同时校验原文。该设计依赖 SHA-256 抗碰撞假设。状态和内部枚举使用有界 VARCHAR。
- 替换 AUTOINCREMENT、PRAGMA、ON CONFLICT、INSERT OR IGNORE、字符串拼接与 SQLite JSON 查询；MySQL JSON 字符串查询需要正确去除 JSON 引号。
- MySQL 条件唯一约束使用等价的 NULL 唯一索引语义；重复写入处理不能吞掉非重复键错误。
- 迁移版本记录、重复启动和失败重试应有测试，考虑 MySQL DDL 隐式提交，不承诺 DDL 可整体事务回滚。
- 更新 `run.sh`、后端运行文档和 Playwright 数据库配置，移除旧数据库文件参数。

### S2.1 schema baseline 2

- S2.1 采用新的空 schema baseline `2`，按 Execution、Transcript、Telemetry、Trace、Operations 数据所有权重新建表；不迁移、回填或双写当前 baseline `1` 数据。
- 完整 baseline 2 DDL 在 S2.1-S2 先由失败测试锁定，再一次性更新 `backend/src/main/resources/schema.sql`。数据库仍由部署方显式创建表；应用不得执行 DDL、自动升级、自动清库或把不匹配版本修正为 `2`。
- 启动时必须读取唯一的 schema 版本记录并精确校验为 `2`。缺表、多版本、未知版本或 baseline 不匹配均返回稳定配置错误，错误和日志不得包含 JDBC URL、账号或密码。
- 需要从旧代码回滚时，代码与空 baseline `1` schema 成对回滚；不得对 baseline `2` 数据库执行原地降级。后续若 DDL 变化，先更新规格并递增 baseline，禁止版本值不变时静默改表。
- `codex_analyze_test` 与正式库执行相同版本校验。任何自动测试只能清理已经核实为测试库的合成数据，不能自行 drop/recreate 用户数据库；重建动作由用户显式执行。

### S2.2 目标 schema baseline 3

用户于 2026-09-18 确认 V1.0 选择 OTel + Transcript。baseline 2 保留为 Hook-first 历史实现，不允许在版本号不变时删除 Hook 表或改变 Execution 所有权。代码迁移 Story 必须使用新的空 schema baseline `3`，不迁移、回填或双写 baseline 2 数据。

目标所有权如下：

| 上下文 | 逻辑表 | 关键约束 |
| --- | --- | --- |
| Schema | `schema_metadata` | 唯一记录必须精确为 `3`；应用只校验，不自动建表、升级或清库 |
| Execution | `execution_raw_record`、`execution_normalization_job` | OTLP 协议身份或 `(batch_id, object_index)` 幂等；原始证据只追加；记录与任务同事务创建 |
| Execution | `execution_session`、`execution_turn` | Session 以 `conversation_id` 唯一；Turn 以 `(conversation_id, turn_id)` 唯一；状态单调且保存 revision |
| Execution | `execution_event`、`execution_performance_span` | 分别保存可重建的 `TurnEvent` 时间点证据与 `TimedOperation` Span 区间证据，不代表独立聚合根；Event 按协议身份幂等，Span 以 `(trace_id, span_id)` 唯一；Span 的 Turn 身份、模型调用身份及可选事件关联分别核验，不能用时间接近或父子 Span 身份推定工具归属；父子关系和事件自身时间不得用接收顺序替代 |
| Execution | `execution_change` | Execution 聚合/实体变化同事务追加单调序列 |
| Transcript | `transcript`、`transcript_item`、`transcript_command_execution`、UNKNOWN 相关表、`transcript_change` | 延续 Path/generation/byte offset 幂等、Meta Session ID 和内容侧 Turn/Call 候选；命令子执行以 Path、generation、来源 Item offset 和 execution ID 保持父子层级与结果事实 |
| Trace | `trace_transcript_evidence_link`、`trace_tool_alignment` | 两侧身份、revision、等级和算法版本幂等；未验证公共工具 ID 时禁止 `EXACT`；一个模型 Tool Call 可关联多个命令子执行，不扁平化 |
| Trace | `trace_turn_view`、`trace_projection_checkpoint` | 每个 `(conversation_id, turn_id)` 一份读模型；关系与读模型提交后才推进检查点 |

baseline 3 不包含 `execution_raw_hook_event`、Hook normalization job 或 Hook `tool_use_id` 作为目标业务身份。现有表只能随 baseline 2 代码保留作回滚，不能在 baseline 3 中以“兼容字段”继续写入。DDL、索引、外键和容量上限在新的代码迁移 Story 中先由失败测试锁定，再由用户显式重建空测试库；本轮技术方案决策不执行数据库操作。

baseline 3 的计数和耗时约束同时锁定：模型 Tool Call 按已验证的模型 Call ID 计数，`CommandExecution` 按子执行身份计数；父 Tool Call 耗时保存 OTel 父 Span 或经版本验证的 OTel 调用整体区间，不保存“子执行耗时求和”作为父耗时。具体列、外键及删除策略仍须在代码迁移 Story 的失败测试和文件/表级迁移清单中确认。

## 验证要求

- 2026-09-15：用户已在现有本机 MySQL 创建 `codex_analyze_test` 及表，集成验收复用该实例的独立 schema，不再启动临时 MySQL。测试使用继承的账号密码环境变量，在每次清理前核实 `SELECT DATABASE()` 为 `codex_analyze_test`；只清理该测试库的合成数据，不执行建库或建表。

- 单元测试保持不依赖外部 MySQL、网络、真实数据或本机凭据；数据库集成验证与纯单元测试分离。
- 使用隔离的 MySQL 测试库验证建表、Mapper、JSON 提取、重复事件、乱序合并、事务回滚、Unicode 正文、大小写不同的 ID、长文本及重启幂等。
- 内存数据库兼容模式不能替代真实 MySQL 验证。
- E2E 只能使用独立测试库与合成数据，禁止复用实际采集库；满足仓库全链路实施门禁后才运行浏览器 E2E。
- 未完成真实 MySQL 验证时必须明确报告，不得宣称数据库切换已验收。

## MySQL 参考依据

- [InnoDB 索引限制](https://dev.mysql.com/doc/refman/8.4/en/innodb-limits.html)：长字段不能直接照搬 SQLite 的 TEXT 唯一索引。
- [重复键更新语义](https://dev.mysql.com/doc/refman/8.4/en/insert-on-duplicate.html)：使用无操作更新处理重复键，并启用 Connector/J `useAffectedRows=true`，确保重复 UNKNOWN 关联返回 0，避免重复累计。
