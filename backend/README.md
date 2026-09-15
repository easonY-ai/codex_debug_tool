# Trace Lens 后端

后端 B1 实现 JSONL 原始数据采集：启动发现、目录监听、周期/手动增量扫描、SQLite 持久化与检查点，以及原始记录查询。尚未实现会话/轮次标准化、OTLP、关联、统计诊断、SSE 和前端 API 接入。

## 构建与测试

需要 JDK 21。Maven Wrapper 固定 Maven 3.9.11，并校验发行包 SHA-256；首次运行需要下载 Maven 与依赖。

```bash
cd backend
./mvnw verify
```

测试使用临时目录、临时 SQLite 与人工生成的 JSONL，不读取真实 Codex 数据。覆盖字节偏移、UTF-8/CRLF、半行、未知/损坏记录、事务回滚、文件轮换、目录监听、并发重扫和 API 边界。

## 启动

```bash
java -jar target/trace-lens-backend-0.1.0-SNAPSHOT.jar
```

默认监听 `127.0.0.1:8080`，连接本机已初始化的 MySQL `codex_analyze` 库。启动前设置 `MYSQL_USERNAME` 和 `MYSQL_PASSWORD`；可选 `MYSQL_HOST`、`MYSQL_PORT`。旧 SQLite 文件不会被读取或迁移。

明确配置采集根目录后启用；以下路径是虚构示例，应替换为已授权的本地目录，程序读取其中的 `sessions` 子目录：

```bash
java -jar target/trace-lens-backend-0.1.0-SNAPSHOT.jar \
  --analyzer.jsonl.enabled=true \
  --analyzer.jsonl.root=/workspace/demo-codex \
```

应用不会修改 Codex 配置。采集数据只保存在本机，不向外部发送。开发时 Vite 已代理 `/api` 与 `/v1` 到本机后端。

## 接口

| 接口 | 行为 |
| --- | --- |
| `GET /api/ingestion/status` | 扫描状态、文件检查点、记录数和能力状态；B1 不报告整体“采集正常” |
| `POST /api/ingestion/rescan` | 同步增量补扫；未启用时返回 409 |
| `GET /api/ingestion/records` | 按 ID 游标查询原始事件，默认 50 条，上限 200 |

```bash
curl http://127.0.0.1:8080/api/ingestion/status
curl -X POST http://127.0.0.1:8080/api/ingestion/rescan
curl 'http://127.0.0.1:8080/api/ingestion/records?afterId=0&limit=50'
```

记录查询支持 `sourceId` 和 `parseStatus`（`VALID_JSON`、`INVALID_JSON`、`INVALID_UTF8`）。返回 `items`、`hasMore`、`nextCursor`；后续请求把 `nextCursor` 作为 `afterId`。原始字节通过 JSON Base64 返回，原文保留 CRLF 中的 CR，LF 计入行的 `endOffset`。事件时间只读取可解析的 ISO 时间戳，不使用入库时间填补。

错误返回稳定 `code`，不返回 SQL、原文或堆栈。拒绝非本机 Host 和跨源浏览器请求，不开放 CORS。生产静态资源整合及开发代理在后续实现。

## 增量保证与限制

- 完整行和检查点按批次一起提交，失败时一起回滚。重复扫描只继续未提交字节。
- 未换行的末行留待下次扫描；空行或无效 JSON 也保留原始数据。
- 默认单行最大 16 MiB，超限时保留该行起点并报告文件扫描失败；其他文件继续。可通过 `--analyzer.jsonl.max-line-bytes` 调整，范围为 1 KiB–64 MiB。
- 文件身份变化、截断或检查点前最多 4096 字节发生变化时创建新的 generation，旧记录保留。不会持续校验整个历史前缀；不保证发现更早历史的原地修改。
- 目录监听用于加快发现，默认每 10 秒补扫一次作为兜底。配置项为 `--analyzer.jsonl.scan-interval-ms`。
- 只有语法解析，尚未验证不同 Codex 事件版本，也不将事件关联到会话或轮次。

目录结构：`config` 配置与访问边界、`ingestion` 原始解析和扫描、`persistence` MyBatis Mapper、`api` HTTP 接口。后续功能继续在本目录演进。
