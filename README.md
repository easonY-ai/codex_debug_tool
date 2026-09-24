# Codex Trace Analyzer

用于分析本机 Codex 执行过程与性能瓶颈的可视化工具。

当前 V1.0 的 OTel + Transcript H5 原型用于需求评审；正式代码仍处于 Hook-first 历史实现，OTel-first 迁移 Story 尚未启动。

## 目录

- `prd_and_design/`：产品需求、技术方案、数据关联规则和原型基线说明。
- `prd_and_design/prototype-v1/`：可独立运行的 V1.0 原型源码，同一需求版本的迭代由 Git 历史管理。
- `frontend/`：正式前端代码线，后续在这里接入真实 API、完善测试并生成发布产物。
- `backend/`：Java 17 本机服务，提供 Hook、JSONL/transcript、OTLP 采集，跨源关联、分析查询、UNKNOWN 映射与 SSE。

需求口径以 PRD 为准，界面与交互以当前原型规格为准；发现冲突时先更新需求与原型规格，再迭代对应版本的原型。

## 运行正式前端

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://127.0.0.1:4173/`。生产构建使用 `npm run build`。

## 运行后端

后端构建与运行见 [后端说明](./backend/README.md)。使用 JDK 17 执行 `./package.sh` 生成内嵌正式前端的可执行 JAR；默认关闭真实目录采集，需显式配置后启用。

## 运行 V1.0 原型

```bash
cd prd_and_design/prototype-v1
npm install
npm run dev
```

原型默认使用 `http://127.0.0.1:4174/`，与正式前端端口不同。

## 知识库

- [知识库索引](./prd_and_design/README.md)
- [产品需求](./prd_and_design/01-product-requirements.md)
- [技术设计](./prd_and_design/02-technical-design.md)
- [数据与关联模型](./prd_and_design/03-data-and-correlation.md)
- [H5 原型规格](./prd_and_design/04-prototype-spec.md)
- [原型设计基线](./prd_and_design/05-prototype-baseline.md)

公开仓库中的示例数据全部为人工构造内容，不得提交真实账号、密钥、本机目录或会话数据。
