# V3 OTel + Transcript 原型规格

## 定位与门禁

V3 是 E1-S2.2 的详细产品与交互设计，使用完全虚构的数据验证 OTel-first 的页面语义。`prototype-v1`、`prototype-v2` 保持冻结。V3 原型不接入 Codex、OTLP、JSONL 或 MySQL，也不代表正式代码迁移完成。原型经用户人工确认后，才拆分并启动新的迁移 Story。

事实优先级以 `01-product-requirements.md`、`03-data-and-correlation.md` 和 `16-s2.2-credibility-and-technical-selection.md` 为准。V2 的导航、密度和双层时间轴可作视觉参考，Hook-first 字段和交互不继承。

## 页面与操作

| 页面 | 主要任务 | V3 设计契约 |
| --- | --- | --- |
| 分析总览 | 按 Session ID、标题、用户问题、来源、状态定位 Turn | 主列表只包含有 OTel 骨架的 Turn；指标有统计对象、有效样本、排除项与来源；异常耗时贡献按同类 P50、至少 5 个样本和双阈值计算；Transcript-only 记录进入采集检查区 |
| Trace 详情 | 分析一次 Turn 的行为、性能与内容 | OTel 执行、Transcript 内容、OTel 性能在共享时间坐标下分层展示；节点可选中并检查两侧原始证据、内容、状态和关联等级；时间缩放、节点列表与返回总览可用 |
| 实时会话 | 跟踪运行中的 Turn | OTel 事件形成当前阶段；Transcript 延迟时显示等待补齐，最终回复尚未生成时显示未知；可打开与 Trace 相同的证据检查器 |
| 采集状态 | 区分配置、接收、投递和覆盖问题 | 分别展示有效 OTel 配置、logs/traces/metrics 接收器、已到达请求成功率、业务数据覆盖、Transcript 扫描/路径/Meta；零请求为 IDLE 且成功率未知；允许对已发现文件模拟增量重试 |

## Trace 信息结构

- 身份：`(conversation.id, turn.id)` 是正式 Turn 的完整业务身份。OTel 缺失时不创建正式 Trace；Transcript 原始记录只可在采集检查区查看。
- 执行层：用户入口、模型请求、模型 Tool Call、决策和终态来自 OTel。未验证的事件边界显示无证据，不重建 Hook 回调。
- 内容层：TranscriptItem 保留可见用户/模型正文、工具参数/结果、原始 JSONL；内容延迟、缺失、身份冲突分别显示 `PENDING`、`MISSING`、`CONFLICT`。
- 性能层：只以 OTel logs/traces 计算单次 API、TTFT、工具与父子 Span；Metric 仅用于聚合趋势。审批展示批准/拒绝事实，等待耗时为未知，不产生等待诊断。
- 工具：`ModelToolCall(callId)` 下可有多个 `CommandExecution(executionId)`。父调用计数 1，子执行计数 N；子执行各自展示 Transcript `status/exit_code`，缺失为 `UNKNOWN`。父耗时以 OTel 父 Span 或已验证整体区间为准，不能对子执行求和。
- 耗时摘要：模型请求、工具执行、本地处理、未归因区间之和等于 Turn 总耗时；并行区间只计一次。审批无可验证等待区间时属于未归因。诊断不含审批等待过长。
- 完整度：OTel Session/Turn 生命周期、OTel 工具/请求事件、Transcript 内容、OTel 性能 Span 四项等权；关联等级独立展示。无 OTel 骨架时不显示正式完整度。
- 关联检查：`EXACT` 需要当前版本验证的公共身份；`BOUNDED` 只保证同 Session/Turn；`INFERRED` 显示类型、时间和顺序证据；多候选为 `UNMATCHED`。0.154.0 本地模型 Call ID 可精确关联，不外推 hosted tools 或其他版本。

## 原型场景

1. 完整 Turn：两个 OTel 模型请求、本地工具成功、Transcript 已补齐，可查看精确 Tool Call 身份。
2. 并行命令：一个模型 Tool Call 下两个 `CommandExecution`，一个退出失败；OTel `tool_result.success` 不覆盖 Transcript 命令结果。
3. 审批决策：展示批准事实和来源，等待耗时未知，对应间隔计入未归因。
4. Transcript 延迟：OTel Trace 可用，内容为 `PENDING`；模拟补齐后恢复并重算。
5. Transcript-only：采集检查区保留原始记录，不出现在正式 Turn 列表。
6. OTel 空闲：logs/traces/metrics 零请求时为 `IDLE`、成功率未知、覆盖未知；有效配置与接收状态独立。
7. 身份冲突、并行工具候选歧义、缺失终态：各保留原始证据，不静默合并或合成成功。

所有示例使用固定虚构 ID、`/workspace/demo-project` 路径和虚构正文。Hosted tool 只允许在 Turn 级显示影响或最终可见内容，不绘制虚构的单次 hosted tool 节点。

## 人工评审用例

| 编号 | 操作 | 通过标准 |
| --- | --- | --- |
| V3-01 | 从总览筛选并打开并行命令 Turn | 父调用 1、子执行 2，失败命令由 Transcript 证据判定，父耗时不求和 |
| V3-02 | 在 Trace 切换执行、内容、性能层并选择节点 | OTel 与 Transcript 原始证据分开，关联等级和版本依据可查；缩放与节点列表可用 |
| V3-03 | 查看审批节点及本轮耗时摘要 | 只有决策，无审批等待耗时或诊断；总耗时守恒 |
| V3-04 | 查看内容延迟和 Transcript-only 场景 | 有 OTel 时 Trace 可见但内容待补；只有 Transcript 时仅可检查原始记录 |
| V3-05 | 查看采集状态并切换零请求场景 | 接收器监听、投递 IDLE、成功率未知、覆盖 UNKNOWN 分别可见 |
| V3-06 | 在实时页选择运行中 Turn 并打开事件检查器 | 显示 OTel 当前阶段、Transcript 等待补齐和未生成输出，不伪造终态 |
| V3-07 | 在桌面与窄屏检查四个页面 | 内容不重叠，主要操作可用，冻结 V1/V2 未被修改 |

## 后续边界

V3 人工确认后，新的代码迁移 Story 需先定接口与测试，并列出 Hook 旧文件/表停写和删除清单、baseline 3 DDL、OTLP binary protobuf、Trace 投影与正式前端改造。原型中的状态切换只用于评审交互，不是已接通的数据链路。

## 验证与人工门禁记录

| 日期 | 范围 | 结果 |
| --- | --- | --- |
| 2026-09-24 | `prototype-v3` TypeScript 检查与 Vite 生产构建 | 通过；大于 500 kB 的 bundle 提示只影响原型加载体积，不代表正式前端打包已完成 |
| 2026-09-24 | `npm test` 纯计算单元测试 | 4 项通过：分位数、异常贡献门槛、耗时守恒、四项完整度；不依赖网络、数据库或真实 Codex 数据 |
| 2026-09-24 | Playwright 独立浏览器，1440px / 390px | HTTP 200、最终 URL 与标题正确；总览筛选、趋势画布、样本不足、并行子执行分泳道、节点证据检查、实时页、采集零请求场景均可用；页面无脚本错误或整体水平溢出 |
| 2026-09-24 | V3-02 原始证据展示修复 | `运行支付回调测试` 节点现明确显示 2 条关联的 Transcript 完整 JSONL 记录，并可切换查看该虚构 Transcript 文件全部 6 行；单元测试增至 6 项，1440px / 390px Playwright 复验通过 |
| 2026-09-24 | V3-01 至 V3-07 人工评审 | 用户确认当前 V3 原型；后续按 Story 开发，具体细节在对应 Story 中调整。原型设计人工门禁通过，正式代码迁移尚未启动 |

本机依赖安装镜像未返回结果；自动构建使用仓库现有 V2 的同版本 `node_modules` 本地副本。原型目录仅跟踪源码与依赖声明，依赖目录、构建产物和 TypeScript 缓存均被忽略。需要独立复现时按原型 README 的命令安装依赖。
