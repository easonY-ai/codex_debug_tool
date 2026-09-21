# Codex Trace Analyzer 知识库

本目录是项目需求、技术决策和原型范围的事实来源。实现与本文档冲突时，应先更新文档并说明决策原因，再修改代码。

## 文档索引

1. [产品需求](./01-product-requirements.md)：产品目标、用户场景、功能范围和验收标准。
2. [技术设计](./02-technical-design.md)：技术栈、系统边界、采集流程、接口与交付方式。
3. [数据与关联模型](./03-data-and-correlation.md)：Hook、JSONL、OTel 的职责，以及跨数据源关联的可信度规则。
4. [H5 原型规格](./04-prototype-spec.md)：第一阶段页面、交互、Mock 数据和验收门槛。
5. [原型设计基线](./05-prototype-baseline.md)：已确认 V1 的源码位置、冻结范围、参考顺序和变更规则。
6. [MySQL 切换方案](./06-mysql-migration.md)：MySQL 与环境变量需求、实现影响和编码前门禁。
7. [主链路集成测试](./07-main-flow-acceptance.md)：Hook 采集与 Trace 绘制的集成测试用例、执行顺序和人工验收记录。
8. [Epic/Story 拆分与进度](./08-epics-and-stories.md)：项目阶段、Epic/Story 范围、状态和人工门禁。
9. [Hook 采集与 Trace Story 规格](./09-hook-trace-story-spec.md)：当前最高优先级 Story 的接口、数据流、验收标准和代码阅读路线。
10. [领域化架构升级 Story 规格](./10-domain-architecture-story-spec.md)：Story0 的 DDD 分层、结构化接口、Raw Event 特例和 Story1 回归门禁。
11. [本地运行日志 Story 规格](./11-local-runtime-logging-story-spec.md)：S1.1 的 CLI/后端日志边界、脱敏、轮转和验收用例。
12. [Transcript 内容补齐 Story 规格](./12-transcript-content-story-spec.md)：S2 的绑定安全、结构化内容、领域边界和人工主链路门禁。
13. [问题域与聚合边界架构升级 Story 规格](./13-domain-modeling-architecture-upgrade-story-spec.md)：S2.1 的问题域澄清、限界上下文、聚合边界和 Repository 语义重构门禁。
14. [OTel + Transcript 数据源方案升级 Story 规格](./14-otel-only-feasibility.md)：E1-S2.2 的方案对比、采样证据、最终来源裁决、Story 拆分和决策门禁。
15. [V1.0 OTel + Transcript 来源覆盖可行性](./15-v1-source-coverage-feasibility.md)：进入 V3 详细产品方案前的能力覆盖矩阵、关键技术卡点、验证方法和通过标准。
16. [S2.2 可信性分析与关键技术方案选型报告](./16-s2.2-credibility-and-technical-selection.md)：可独立恢复上下文的证据分级、真实样本结论、方案对比、已确认裁决、剩余风险和续接顺序。

## 已确认决策

- 产品是运行在本机的 Web 应用，仅监听回环地址。
- 覆盖本机 Codex Desktop、CLI 和 IDE 产生的会话。
- Vue H5 原型 V1 已冻结在 `prototype-v1`，不接入真实数据。
- Hook-first 采集与兼容性方案 V2 已确认，冻结在 `prototype-v2`；正式前后端按该版本进入编码。
- 正式前端代码线位于 `../frontend`，后续真实功能只在该目录实现。
- 原型确认后才开发 Java 后端；正式前端在后端可用前允许暂时保留 Mock 数据适配层。
- 后端采用 Java 17、Spring Boot、MyBatis；数据库使用本机 MySQL，配置与验收见 MySQL 切换方案。
- 前端采用 Vue 3、TypeScript、Vite、Element Plus 和 Apache ECharts。
- V2 曾确认 Hook 是 Session、Turn 和 Tool Call 骨架的主事实来源；用户于 2026-09-18 确认 V1.0 调整为 OTel + Transcript、OTel-first。OTel 拥有核心行为遥测与性能事实，Transcript 拥有可见内容，Hook 不进入目标架构；V1.0 覆盖产品分析语义，不承诺逐事件复刻 Hook。现有 Hook 实现仅在迁移完成前保留；Tool `EXACT` 只适用于版本化样本已验证的本地工具模型 Call ID，不外推到 hosted tools 或其他版本。
- 本地数据库可以保存完整内容，但公开仓库不得包含任何真实会话、账号或机器信息。
- 运行配置统一由 `backend/src/main/resources/application.yml` 管理；敏感值只在该文件使用环境变量占位符注入，Java 代码不得手工读取环境变量或硬编码连接池参数。

## 迭代规则

- 新需求先写入产品需求，再修改原型或实现。
- 新的架构选择写入技术设计，并记录替代方案及选择原因。
- 数据字段或关联规则变化必须同步更新数据与关联模型。
- 原型范围变化必须同步更新原型规格和验收清单。
- 已冻结的原型目录只读；产品或交互变化需要先更新需求，再创建新的原型版本，不能覆盖 V1。
- 示例数据始终使用虚构内容，不从真实 Codex 会话复制或改写。

## 当前里程碑

Hook-first 能力原型 V2 已确认并作为历史基线冻结，但目标数据源方案已升级。根据可行性先行规则，项目当前仍处于“需求澄清（V1.0 双源覆盖验证）”阶段。E0-S0、E1-S1、E1-S1.1 和 E1-S2 均已完成并由用户接受；E1-S2.1 的 Hook-first Execution 重构已完成代码及 61 项自动测试，但暂停人工验收。当前 Story E1-S2.2 的 V1.0 方案已经用户确认：融合 OTel + Transcript，并以 OTel 为核心行为遥测与性能来源。F-01、F-02、F-04、F-05 已通过；2026-09-21 最新 237 个 OTLP 批次证明本地工具模型 Call ID 可精确关联，并覆盖成功、失败、中断和缺失终态。用户已确认 hosted tool 级身份、状态和耗时移出 V1.0，并登记为 V1.1 TODO，同时确认本地命令结果由 Transcript `CommandExecution.status/exit_code` 拥有。进入 V3 前仅剩并行子执行模型需要人工确认。
