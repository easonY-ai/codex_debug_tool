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

- **已明确**：数据库选型变更由用户直接授权，不改变 Hook-first 数据语义、关联规则和指标口径。
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

## 验证要求

- 单元测试保持不依赖外部 MySQL、网络、真实数据或本机凭据；数据库集成验证与纯单元测试分离。
- 使用隔离的 MySQL 测试库验证建表、Mapper、JSON 提取、重复事件、乱序合并、事务回滚、Unicode 正文、大小写不同的 ID、长文本及重启幂等。
- 内存数据库兼容模式不能替代真实 MySQL 验证。
- E2E 只能使用独立测试库与合成数据，禁止复用实际采集库；满足仓库全链路实施门禁后才运行浏览器 E2E。
- 未完成真实 MySQL 验证时必须明确报告，不得宣称数据库切换已验收。

## MySQL 参考依据

- [InnoDB 索引限制](https://dev.mysql.com/doc/refman/8.4/en/innodb-limits.html)：长字段不能直接照搬 SQLite 的 TEXT 唯一索引。
- [重复键更新语义](https://dev.mysql.com/doc/refman/8.4/en/insert-on-duplicate.html)：使用无操作更新处理重复键，并启用 Connector/J `useAffectedRows=true`，确保重复 UNKNOWN 关联返回 0，避免重复累计。
