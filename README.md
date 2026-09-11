# Codex Trace Analyzer

用于分析本机 Codex 执行过程与性能瓶颈的可视化工具。

当前已把评审通过的 H5 原型冻结为 V1 设计基线，并从该基线建立独立的正式前端工程。正式前端目前仍使用虚构 Mock 数据；后续真实功能只在 `frontend` 中实现，冻结原型不随实现调整。

## 目录

- `prd_and_design/`：产品需求、技术方案、数据关联规则和原型基线说明。
- `prd_and_design/prototype-v1/`：可独立运行的冻结原型源码，只用于确认 UI、交互和产品语义。
- `frontend/`：正式前端代码线，后续在这里接入真实 API、完善测试并生成发布产物。
- `backend/`：Java 采集与分析服务的预留目录，待后端实施开始时创建。

需求口径以 PRD 为准，界面与交互以冻结原型为准；实现发现冲突时先更新需求并形成新的原型版本，不能直接改写 V1。

## 运行正式前端

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://127.0.0.1:4173/`。生产构建使用 `npm run build`。

## 运行冻结原型

```bash
cd prd_and_design/prototype-v1
npm ci
npm run dev
```

冻结原型与正式前端默认使用同一端口，请不要同时启动。

## 知识库

- [知识库索引](./prd_and_design/README.md)
- [产品需求](./prd_and_design/01-product-requirements.md)
- [技术设计](./prd_and_design/02-technical-design.md)
- [数据与关联模型](./prd_and_design/03-data-and-correlation.md)
- [H5 原型规格](./prd_and_design/04-prototype-spec.md)
- [原型设计基线](./prd_and_design/05-prototype-baseline.md)

公开仓库中的示例数据全部为人工构造内容，不得提交真实账号、密钥、本机目录或会话数据。
