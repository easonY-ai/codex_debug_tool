# Codex Trace Analyzer Frontend

这是正式前端代码线，从已确认的 H5 原型 V1 初始化。后续真实 API 接入、工程化拆分、自动化测试和发布构建均在此目录进行；不要直接修改 `prd_and_design/prototype-v1`。

生产入口使用本机后端 API 和 SSE；合成数据仅保留给离线单元测试，不进入正式页面运行链路。

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

## 工程结构与验证

- `src/data/`：页面数据契约、真实 API 适配器，以及仅供测试使用的合成适配器。
- `src/analysis/`：可独立测试的异常贡献与筛选后总览指标计算。
- `src/components/`：V1 页面与交互组件。
- `tests/`：指标边界及总览筛选、点击行为测试。

```bash
npm test
npm run build
```

`npm run test:watch` 可持续运行测试。测试仅使用合成数据。

总览指标已随筛选范围重算，TTFT 使用请求级样本，一个轮次可以贡献多个请求；失败、取消或没有可见文本增量的请求不进入有效样本。

尚未完成：逐轮 Trace 数据（当前仍为第一轮演示详情）、随筛选更新的趋势、实时与采集状态数据适配、异步加载与错误态、Playwright 核心路径、后端采集与 API 接入。数据注入接口目前是静态展示快照，不应直接作为后端传输协议。
