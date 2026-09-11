# Codex Trace Analyzer Frontend

这是正式前端代码线，从已确认的 H5 原型 V1 初始化。后续真实 API 接入、工程化拆分、自动化测试和发布构建均在此目录进行；不要直接修改 `prd_and_design/prototype-v1`。

当前阶段仍使用合成 Mock 数据，因此“正式前端”表示可持续演进和发布的工程目录，不表示 JSONL、OTLP 或后端数据链路已经接通。

## 本地运行

```bash
npm ci
npm run dev
```

访问 `http://127.0.0.1:4173/`。

## 构建

```bash
npm run build
```

产品口径以 `../prd_and_design/01-product-requirements.md` 为准，界面与交互参考冻结原型，数据关联以 `../prd_and_design/03-data-and-correlation.md` 为准。
