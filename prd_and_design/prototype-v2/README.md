# H5 原型 V2（已确认基线）

这是 2026-09-14 确认的 Codex Trace Analyzer V2 基线，采用 Hook-first：Hook 建立骨架，JSONL 补齐内容，OTel 补齐性能，并包含 transcript/session_meta 健康、UNKNOWN 映射、实时会话和 Trace 三源证据。

本目录只使用合成 Mock 数据，不读取本机 Codex 会话，不接收 Hooks 或 OTLP，也不写入数据库。正式前后端可以按该基线开始实现。

## 运行

```bash
npm ci
npm run dev
```

访问 `http://127.0.0.1:4173/`。使用 `npm run build` 验证生产构建。

## 冻结规则

- 本版本已经冻结；后续产品或交互变化应创建 V3，不回写 V2。
- 冻结 V1 和 V2 均保持只读；正式实现位于仓库根目录的 `frontend` 与 `backend`。
- 不提交 `node_modules`、`dist`、真实账号、本机路径、会话原文或任何凭据。

基线范围和参考顺序见 [`../05-prototype-baseline.md`](../05-prototype-baseline.md)。
