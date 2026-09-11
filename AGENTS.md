# 仓库协作规则

## 必须读取的项目上下文

在规划或实施任何修改前，必须完整阅读 `prd_and_design/README.md`，并继续完整阅读其中“文档索引”引用的所有文档。将这些文件视为项目的持久上下文，不得依赖历史对话补充缺失的需求。

处理实现问题时，按以下优先级参考：

1. `prd_and_design/01-product-requirements.md` 定义产品语义、指标口径和数据边界。
2. `prd_and_design/prototype-v1` 定义已确认的 V1 页面结构、视觉层级和交互方式。
3. `prd_and_design/03-data-and-correlation.md` 定义 JSONL、OTel 和跨数据源关联规则。
4. `prd_and_design/02-technical-design.md` 定义工程实现方式。

如果这些来源互相冲突，或者无法确定一项会实质影响产品的决策，不得自行选择或静默假设。必须说明冲突，并先更新需求，再开始实现。

## 受保护的设计基线

- `prd_and_design/prototype-v1` 是已冻结、可复现的设计基线。不得修改、格式化或重新生成该目录中的任何文件。
- 产品或交互发生变化时，必须先更新 PRD 和原型规格。需要新的可运行原型时，创建 `prd_and_design/prototype-v2` 等新版本，禁止覆盖 V1。
- 正式前端功能只能在 `frontend` 中实现。
- `backend` 目录及其里程碑建立后，后端功能只能在 `backend` 中实现。

## 文档同步与仓库安全

- 已确认的需求、指标口径、架构、数据结构或关联规则发生变化时，必须同步更新 `prd_and_design`。
- 本项目是公开仓库。禁止提交真实账号信息、凭据、Token、私钥、本机专属路径、真实 Codex 对话或其他敏感数据。
- 示例、测试数据和截图必须使用明确的虚构内容，例如 `/workspace/demo-project` 和固定测试 ID。
- 禁止提交依赖目录、构建产物、本地数据库、JSONL 采集文件、日志或本地配置。
