# Hook/Trace 主链路集成测试与验收记录

## 执行策略

- 当前只执行 `IT-HOOK-TRACE-001`，且由用户手工执行。
- 该用例通过并完成代码阅读后，必须由用户明确同意，才能开始其他用例。
- 历史自动化结果只作为回归基线，不代替本次真实 Codex 人工验收。
- 真实数据只留在本机，不得将会话、凭据、本机路径或日志提交到公开仓库。

## 人工环境准备

### 1. 前置检查

在仓库根目录执行：

```bash
codex --version
java -version
uv run --project cli python --version
```

已知基线是 Codex CLI 0.154.0、JDK 17 和由 uv 解析的 Python 3.11。如 Codex 版本已变化，先更新 Hook fixture 与契约。

### 2. 生成项目级 Hook 配置

从仓库根目录执行：

```bash
mkdir -p .codex
uv run --project cli python cli/generate_hooks.py \
  --command 'uv run --project "$(git rev-parse --show-toplevel)/cli" python "$(git rev-parse --show-toplevel)/cli/trace_lens_hook.py"' \
  --output .codex/hooks.json
```

这会生成项目级 `.codex/hooks.json`，不修改 `~/.codex/config.toml`。命令通过 Git 根目录定位 forwarder，不依赖 Codex 启动时的子目录。不要使用当前未跟踪的 `cli/hooks.json`，其中相对命令依赖特定 cwd。

在 Codex 中输入 `/hooks`：

1. 确认来源是当前项目的 `.codex/hooks.json`。
2. 审查实际命令与事件列表。
3. 手工信任该 Hook。
4. 如同一配置层还有内联 `[hooks]`，先移除重复表达，避免重复投递。

Codex 只在项目配置层受信任后加载项目 Hook，修改 Hook 后需重新审查；见 [OpenAI 官方 Hooks 文档](https://learn.chatgpt.com/zh-Hans/docs/hooks)。

### 3. 启动后端

在当前 shell 设置 `MYSQL_USERNAME` 和 `MYSQL_PASSWORD`，但不把凭据写进命令、文档或日志。然后在仓库根目录执行：

```bash
./package.sh
./run.sh \
  --analyzer.jsonl.enabled=true \
  --analyzer.jsonl.root="$HOME/.codex"
```

`package.sh` 先执行前端单元测试与构建，再执行后端 `verify` 并生成包含正式前端的 JAR。如本轮已对当前提交成功执行过 `package.sh`，可直接运行 `run.sh`。

验证：

```bash
curl -sS http://127.0.0.1:8080/api/ingestion/status
curl -sS http://127.0.0.1:8080/api/ingestion/hooks/status
```

如 `CODEX_HOME` 不是 `~/.codex`，将 `analyzer.jsonl.root` 改为实际根目录；该目录必须包含 `sessions` 子目录。

### 4. 启动正式前端

另开终端：

```bash
cd frontend
npm run dev -- --port 4173
```

打开 `http://127.0.0.1:4173/`。

## IT-HOOK-TRACE-001：真实 Codex Hook 到 Trace 骨架

### 目标

验证真实 Codex 事件经项目 Hook、Python forwarder、Java 接收器、MySQL、查询 API 和正式 Vue 前端后，在 Trace 页建立行为骨架。

### 手工步骤

1. 记录 `GET /api/ingestion/hooks/status` 中当前 `accepted` 计数。
2. 确认 `/hooks` 中项目 Hook 已信任，然后新建一个 Codex 会话，以同时覆盖 SessionStart。
3. 在新会话中发送固定提示：

   ```text
   请使用 Bash 执行 printf 'trace-lens-manual-001\n'，然后只回复 trace-lens-manual-001。
   ```

4. 等待 Codex 完成，确认 Hook 未阻断轮次，最终回复是 `trace-lens-manual-001`。
5. 再次查询 Hook 状态，确认 `accepted` 至少增加 4，且 `lastSuccessAt` 已更新。
6. 在分析总览搜索 `trace-lens-manual-001`，确认只有一个对应执行轮次且状态为完成。
7. 点击该行进入 Trace，展开“可访问的时间轴节点列表”，确认至少有 `UserPromptSubmit`、`PreToolUse`、`PostToolUse` 和 `Stop`。
8. 点击 `PreToolUse` 和 `PostToolUse`，检查 Hook 原始证据中的 `session_id`、`turn_id`、`tool_use_id`、`tool_name`；两个工具边界应使用同一 `tool_use_id`。
9. 查看完整度与性能层。如本轮没有 OTel，必须显示缺失/部分，TTFT 不得凭 Hook 时间生成。
10. 按 `09-hook-trace-story-spec.md` 的顺序阅读代码，对照真实 ID 理解数据流。

### 通过标准

- 步骤 1–10 全部通过。
- 工具 Hook 节点不重复，Pre/Post 关联为同一工具调用。
- 页面结论与可检查的原始 Hook 证据一致。
- 任何缺失源都诚实降级，不伪造性能数据或精确关联。

### 失败时记录

只提供失败步骤、`/hooks` 来源/信任状态/错误摘要、脱敏的 Hook status 计数、浏览器错误和 HTTP 状态码；不粘贴数据库密码、完整 transcript 或其他真实对话。

## 后续集成测试清单（未授权执行）

| 编号 | 范围 | 关键预期 | 状态 |
| --- | --- | --- | --- |
| IT-HOOK-TRACE-002 | transcript 内容补齐 | session_meta 匹配后补齐用户、工具和最终内容 | 待 S1.1 完成后进入 S2 验收 |
| IT-HOOK-TRACE-003 | OTel 工具性能 | 共享已验证 ID 时为 EXACT，耗时摘要守恒 | 待 S2 验收且 S3.1 完成 |
| IT-HOOK-TRACE-004 | 实时 SSE | 运行中转完成，节点可检查 | 待 S3 验收 |
| IT-HOOK-TRACE-005 | 重复与乱序 | 重复 delivery 去重，Post 先到可补齐，终态不回退 | 待 S4 验收 |
| IT-HOOK-TRACE-006 | 无效耗时 | 反向/负耗时不进入统计 | 待 S4 验收 |
| IT-HOOK-TRACE-007 | transcript 安全与错配 | 越界、符号链接、不可读、session ID 错配均不挂接 | 待 S4 验收 |
| IT-HOOK-TRACE-008 | 半行与重扫 | 不消费未完整行，完成后只导入一次 | 待 S4 验收 |
| IT-HOOK-TRACE-009 | UNKNOWN 映射 | 受限 JSONPath 校验，只重处理当前指纹 | 待 S4 验收 |
| IT-HOOK-TRACE-010 | 缺失来源降级 | 无 OTel 时 TTFT 未知；无 Hook 时不生成正式 Turn | 待 S4 验收 |
| IT-HOOK-TRACE-011 | 同名并行工具 | 无公共 ID 时保持歧义，不提升为 EXACT | 待 S4 验收 |
| IT-HOOK-TRACE-012 | forwarder 故障 | 4xx/5xx/超时/无后端时均退出 0，不泄露原始输入 | 待 S4 验收 |

## 执行记录

| 日期 | 用例 | 执行者 | 结果 | 证据/备注 |
| --- | --- | --- | --- | --- |
| 2026-09-15 | CLI 单元测试 | Codex | 通过 | 4/4；不依赖网络、MySQL 或真实 Codex 数据 |
| 2026-09-15 之前 | 合成数据自动 E2E | 自动化 | 历史通过 | Git 提交与现有 E2E 文件记录 2 项通过；不代替本轮人工验收 |
| 2026-09-15 | IT-HOOK-TRACE-001 | 用户 | 未通过 | 步骤 6 在分析总览找到了对应轮次，但执行轮次表格未展示状态，无法确认为完成；已进入 S1 实现错误修复 |
| 2026-09-15 | 步骤 6 状态展示修复 | Codex | 自动验证通过 | 先增加失败用例，再在总览表格展示成功、失败、运行中；前端 4 个测试文件、18 项通过，生产构建通过；等待用户复验 |
| 2026-09-15 | IT-HOOK-TRACE-001 步骤 1–6 | 用户 | 通过 | 状态展示修复后完成人工复验；下一步执行步骤 7 |
| 2026-09-15 | IT-HOOK-TRACE-001 步骤 7–9 | 用户 | 通过 | Trace 行为节点、原始 Hook 证据与缺源降级展示均通过；下一步是步骤 10 代码阅读 |
| 2026-09-15 | S1 代码阅读 bugfix：原始 Hook 主键回填 | Codex | 自动验证通过 | 实现错误，不改变需求或技术方案：MyBatis 使用 MySQL 生成键回填，移除插入后按 `deliveryId` 查询 ID 的额外往返；新增真实 MySQL 集成测试，后端全量 28 项测试通过 |
| 2026-09-15 | IT-HOOK-TRACE-001 步骤 1–10 | 用户 | 通过 | 用户已完成全部手工步骤和代码阅读；代码阅读发现的 OTel 精确关联契约问题已登记为后续 S3.1，不属于 S1 验收范围 |
| 2026-09-16 | IT-HOOK-TRACE-001 步骤 1–10（Story0 回归） | 用户 | 通过 | 领域化架构升级后重新检查步骤 1–10，通过；该记录是 Story0 对 S1 主链路的人工回归证据 |
| 2026-09-16 | E1-S1 真实 Codex Hook 主链路 | 用户 | 接受 | `IT-HOOK-TRACE-001` 全部步骤和代码阅读完成，用户明确确认“S1 OK”；Story 结束 |

## 当前结论

E1-S1 已完成。Story0 已由用户确认完成，`IT-HOOK-TRACE-001` 步骤 1–10 已在升级后重新通过，且用户已明确接受 S1。当前下一项为 E1-S1.1 本地运行日志；在用户明确启动前，不执行其他用例或后续 Story。
