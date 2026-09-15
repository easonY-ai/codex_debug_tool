# 主链路集成测试与验收记录

## 环境与边界

- 2026-09-15：使用用户预建的本机 `codex_analyze_test`，已只读确认数据库可连接且有 15 张表。不启动新 MySQL，不创建表。
- 使用合成会话与临时 transcript；不修改真实 Codex 配置。测试基类在清理数据前检查实际数据库名称。
- 后端测试入口：设置本地 `MYSQL_USERNAME`、`MYSQL_PASSWORD` 和 JDK 17 后运行 `python3 backend/scripts/test_mysql.py`。

## 测试用例

| 编号 | 输入与步骤 | 预期结果 |
| --- | --- | --- |
| M01 三源完整链路 | 生成 Hook 配置，通过实际 forwarder 投递用户入口、工具 Pre/Post、Stop；写入匹配 session_meta 的 JSONL；投递 OTLP；总览进入 Trace | 唯一轮次、正确正文与工具结果；三源原文可检查，关联等级有证据；摘要和等于总耗时 |
| M02 实时更新 | 打开实时页后依次投递入口、工具、结束事件 | SSE 驱动更新；运行中转为完成；缺失输出明确标记，节点可检查 |
| M03 幂等与乱序 | 同 deliveryId 重复投递；Post 先到，Pre 后到；另测反向时间边界 | 原始投递去重；工具仅一个且终态不回退；负耗时不进入统计 |
| M04 延迟内容补齐 | 已绑定 transcript 追加半行，补完换行；连续两次手动补齐 | 半行不消费；完整行补齐一次；重复操作不增加记录 |
| M05 缺失与错误来源 | Hook 无 OTel；JSONL 无 Hook；session_meta ID 不匹配；同名并行工具缺少公共 ID | TTFT 未知；不生成无 Hook 正式轮次；错配正文不挂接；歧义不提升为精确 |
| M06 UNKNOWN 映射 | 注入未知结构，界面预览合法与非法 JSONPath，保存并重处理 | 非法表达式拒绝；合法映射仅重处理指定指纹，原始记录保留 |

## 本次实际结果

- Java 17 基线：系统 Temurin 17.0.19；Maven 以 `release 17` 编译，版本门禁限定 `[17,18)`。
- 前端离线测试：4 个文件、17 项通过。
- Python CLI 单元测试：4 项通过。
- 后端 clean verify：27 项全部通过，包含真实 MySQL 集成测试。已修复 DataSource 返回类型、JSON 字符排序规则冲突，并明确 EXPLAIN 输出格式；事务回滚测试改为 Java 注入异常，不再创建触发器。
- 浏览器 E2E：2 项通过。测试通过实际 Python forwarder 投递 Hook，经过 Spring 后端、`codex_analyze_test`、查询 API 和正式 Vue 前端；覆盖三源 Trace、精确工具关联、耗时守恒以及 UNKNOWN 映射。
- M03 的重复、乱序、终态单调和负耗时由后端真实 MySQL 集成测试覆盖；M04 的完整行、断点与重复扫描幂等由后端集成测试覆盖。
- 验收环境已使用 Java 17 启动在 `http://127.0.0.1:4173/`。合成轮次 `turn-e2e-001` 状态为完成，Hook/transcript/OTel 覆盖均为 100%，工具为精确关联，109ms 总耗时拆分为 107ms 工具和 2ms 未归因。
- `DatabaseConfigurationTest` 已按当前连接池大小 20 同步断言。
- M02 实时状态变化、M04 页面手动补扫、M05 全部降级场景和 M06 非法 JSONPath 仍需按下面步骤手动检查。当前模型请求、审批和本地处理的完整统计能力尚未实现，不能把本次主链路测试表述为完整产品验收。

## 手动验收步骤

1. 打开 `http://127.0.0.1:4173/`，在分析总览找到用户问题“真实链路 E2E 测试”，确认状态为成功、轮次为 `turn-e2e-001`。
2. 点击该行进入 Trace，展开“可访问的时间轴节点列表”，确认存在 UserPromptSubmit、PreToolUse、PostToolUse、Stop 和 OTel shell 节点。
3. 点击 PostToolUse，检查 Hook 原始事件、工具输出；点击跨源关联，确认显示“精确”，证据来自 Hook `tool_use_id` 与 OTel `call_id`。
4. 检查本轮耗时摘要：分类之和应等于总耗时；当前数据约显示总耗时 0.1s、工具 0.1s、未归因 0.0s，精确毫秒值见接口结果。
5. 打开“采集状态”，确认 transcript 路径状态 VALID、session_meta 校验 MATCHED、OTLP traces 已接收，UNKNOWN 指纹为 MAPPED。
6. 点击 UNKNOWN 的“配置解析”，先输入不支持的 JSONPath 并确认被拒绝；再使用 `$.payload.content[*].text` 预览，确认匹配 `Synthetic UNKNOWN preview`。
7. 打开“实时会话”，另开终端通过 forwarder 投递一个新的合成轮次，确认页面从运行中更新为完成；点击最新事件检查原始证据。
8. 任一步不符合预期即记录为未通过；自动化通过不能覆盖手动检查失败。
