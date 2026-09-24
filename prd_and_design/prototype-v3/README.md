# Trace Lens V3 原型

独立的 OTel + Transcript 产品交互原型，所有数据均为虚构 Mock。无网络采集、数据库写入或本机 Codex 读取。范围与评审步骤见 `../17-v3-prototype-spec.md`。

```bash
npm install
npm run dev
```

默认地址：`http://127.0.0.1:4174/`。使用 `npm test` 和 `npm run build` 验证计算规则、类型与构建。单元测试使用 Node 24 内置测试运行器。V1/V2 冻结目录不受影响。
