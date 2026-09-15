# Trace Lens 后端

后端已接入本机 MySQL，实现 JSONL 原始采集、Hook 原始接收与 Session/Turn/Tool 骨架、transcript 补齐、OTLP/HTTP JSON 接收、部分跨源关联、会话/Trace 查询、UNKNOWN 映射与 SSE。当前处于 E1-S1 集成测试：Hook 行为骨架是人工验收范围，完整 API/TTFT/审批/本地处理归因与全部产品指标尚未验收，不得宣称整体交付完成。

## 构建与测试

需要 JDK 17。Maven Wrapper 固定 Maven 3.9.11，并校验发行包 SHA-256；首次运行需要下载 Maven 与依赖。

```bash
cd backend
./mvnw verify
```

纯单元测试不读取真实 Codex 数据，也不依赖外部 MySQL。真实 MySQL 集成测试使用用户预建的独立 `codex_analyze_test` schema 和合成数据，通过 `backend/scripts/test_mysql.py` 运行；清理前必须校验当前数据库名。

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

应用不会修改 Codex 配置。采集数据只保存在本机，不向外部发送。开发时 Vite 已代理 `/api` 与 `/v1` 到本机后端；`package.sh` 负责先构建前端，再把静态资源打入可执行 JAR。

## 接口

| 接口 | 行为 |
| --- | --- |
| `POST /api/ingestion/hooks` | 接收 forwarder 封装的 Hook 原始事件 |
| `GET /api/ingestion/hooks/status` | Hook 接收、重复、失败和处理耗时状态 |
| `GET /api/ingestion/transcripts/status` | transcript 路径与 session_meta 关联状态 |
| `POST /v1/logs`, `/v1/metrics`, `/v1/traces` | 接收仅本机 OTLP/HTTP JSON |
| `GET /api/sessions` | 查询 Hook-first 执行轮次 |
| `GET /api/sessions/{turnId}/analysis` | 查询 Trace 行为、性能、关联、诊断与摘要 |
| `GET /api/events` | SSE 事件流 |
| `GET /api/ingestion/status` | 聚合采集状态和已实现能力 |
| `POST /api/ingestion/rescan` | 同步增量补扫；未启用时返回 409 |
| `GET /api/ingestion/records` | 按 ID 游标查询原始事件，默认 50 条，上限 200 |

```bash
curl http://127.0.0.1:8080/api/ingestion/status
curl -X POST http://127.0.0.1:8080/api/ingestion/rescan
curl 'http://127.0.0.1:8080/api/ingestion/records?afterId=0&limit=50'
```

记录查询支持 `sourceId` 和 `parseStatus`（`VALID_JSON`、`INVALID_JSON`、`INVALID_UTF8`）。返回 `items`、`hasMore`、`nextCursor`；后续请求把 `nextCursor` 作为 `afterId`。原始字节通过 JSON Base64 返回，原文保留 CRLF 中的 CR，LF 计入行的 `endOffset`。事件时间只读取可解析的 ISO 时间戳，不使用入库时间填补。

错误返回稳定 `code`，不返回 SQL、原文或堆栈。拒绝非本机 Host 和跨源浏览器请求，不开放 CORS。

## 增量保证与限制

- 完整行和检查点按批次一起提交，失败时一起回滚。重复扫描只继续未提交字节。
- 未换行的末行留待下次扫描；空行或无效 JSON 也保留原始数据。
- 默认单行最大 16 MiB，超限时保留该行起点并报告文件扫描失败；其他文件继续。可通过 `--analyzer.jsonl.max-line-bytes` 调整，范围为 1 KiB–64 MiB。
- 文件身份变化、截断或检查点前最多 4096 字节发生变化时创建新的 generation，旧记录保留。不会持续校验整个历史前缀；不保证发现更早历史的原地修改。
- 目录监听用于加快发现，默认每 10 秒补扫一次作为兜底。配置项为 `--analyzer.jsonl.scan-interval-ms`。
- 原始 JSONL 采集仍只保证语法与字节边界；只有通过 Hook `transcript_path` 绑定、路径安全与 `session_meta` 校验的文件，才能由已验证适配器向 Hook 骨架补齐内容；未知格式保留为 UNKNOWN。

目录结构：`config` 配置与访问边界、`ingestion` 原始解析和扫描、`persistence` MyBatis Mapper、`api` HTTP 接口。后续功能继续在本目录演进。
