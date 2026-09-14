# Codex Hook 0.154.0 契约 fixture

这些 JSON 是依据 2026-09-14 的 OpenAI 官方 [Codex Hooks 文档](https://learn.chatgpt.com/docs/hooks)人工编写的合成输入，用于固定适配器测试边界，不是真实 Codex 会话导出。

- `hooks.json` 覆盖官方 JSON 配置层级。
- `session-start.json`、`session-end-null-transcript.json` 覆盖主会话起止以及 Session 级事件无 `turn_id`。
- `subagent-start.json`、`subagent-stop.json` 覆盖子代理生命周期和可空的子代理 transcript。
- `pre-compact.json`、`post-compact.json` 覆盖 `manual|auto` 压缩触发器。
- `user-prompt-submit.json`、`stop.json`、`interrupt.json` 覆盖 Turn 入口、正常停止与中断。
- `pre-tool-use.json`、`post-tool-use.json` 覆盖同一虚构工具调用的 `tool_use_id` 配对。
- `permission-request.json` 刻意不含 `tool_use_id`，验证接收器不得把该字段设为审批事件必填项。
- 全部十二种官方事件均有独立 stdin JSON fixture。

fixture 只声明对应发布行为页和 0.154.0 schema 已确认的字段；升级版本时必须重新核验，不能据此推断跨版本稳定性。
