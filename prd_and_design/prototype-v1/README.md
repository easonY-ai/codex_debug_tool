# H5 原型 V1（冻结）

这是 2026-09-11 确认的 Codex Trace Analyzer H5 原型源码快照，用于复现已确认的页面、交互和指标表达。

本目录只使用合成 Mock 数据，不读取本机 Codex 会话，不接收 OTLP，也不写入数据库。它不是后续功能开发目录；真实前端实现位于仓库根目录的 `frontend`。

## 运行

```bash
npm ci
npm run dev
```

访问 `http://127.0.0.1:4173/`。使用 `npm run build` 验证生产构建。

## 冻结规则

- 不在本目录继续添加功能或修正产品设计。
- 需求变化先更新 `../01-product-requirements.md` 和 `../04-prototype-spec.md`，需要原型评审时创建 `prototype-v2`。
- 不提交 `node_modules`、`dist`、真实账号、本机路径、会话原文或任何凭据。

基线范围和参考顺序见 [`../05-prototype-baseline.md`](../05-prototype-baseline.md)。
