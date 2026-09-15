#!/usr/bin/env python3
"""Non-blocking Codex Hook forwarder. Uses only Python's standard library."""

from __future__ import annotations

import json
import sys
import time
import uuid
from http.client import HTTPConnection

VERSION = "0.1.0"
# Hook 进程直接处于 Codex 的交互路径中。1 MiB 足以容纳单个事件，同时为
# 异常大的 stdin 设置明确的内存和后端请求上限；后端采用相同限制，避免两端口径不一致。
MAX_STDIN_BYTES = 1024 * 1024


def envelope(raw: bytes, observed_at: int | None = None, delivery_id: str | None = None) -> bytes:
    if len(raw) > MAX_STDIN_BYTES:
        raise ValueError("stdin_too_large")
    event = json.loads(raw)
    if not isinstance(event, dict):
        raise ValueError("stdin_not_object")
    return json.dumps({
        "schemaVersion": 1,
        "deliveryId": delivery_id or str(uuid.uuid4()),
        "observedAt": observed_at if observed_at is not None else int(time.time() * 1000),
        "forwarderVersion": VERSION,
        "rawEvent": event,
    }, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def deliver(body: bytes, connection_factory=HTTPConnection, sleeper=time.sleep) -> str:
    for attempt in range(2):
        # 仅访问回环地址，且连接预算为 250ms：采集是尽力而为，不能拖慢 Codex 本身。
        connection = connection_factory("127.0.0.1", 8080, timeout=0.25)
        try:
            connection.request("POST", "/api/ingestion/hooks", body=body, headers={
                "Content-Type": "application/json",
                "X-Trace-Lens-Forwarder-Version": VERSION,
                "Host": "localhost",
            })
            response = connection.getresponse()
            response.read()
            if response.status < 500:
                return "accepted" if 200 <= response.status < 300 else "rejected"
        except (OSError, TimeoutError):
            pass
        finally:
            connection.close()
        if attempt == 0:
            # 对临时的后端启动/网络抖动只重试一次，控制 Hook 最坏等待时间。
            sleeper(0.05)
    return "unavailable"


def main() -> int:
    try:
        # Codex 触发 Hook 时会启动本脚本，并将事件 JSON 写入该进程的标准输入；
        # 交互形式类似 `echo '{"event":"..."}' | python trace_lens_hook.py`，而不是
        # `python trace_lens_hook.py '{"event":"..."}'` 这种通过命令行参数（sys.argv）传值的方式。
        # 使用 buffer 按原始字节读取，避免文本解码或换行转换影响 JSON 内容和大小判断；
        # 多读 1 字节用于区分“刚好 1 MiB（允许）”与“超过 1 MiB（拒绝）”，且不会将超大输入全部读入内存。
        raw = sys.stdin.buffer.read(MAX_STDIN_BYTES + 1)
        result = deliver(envelope(raw))
        if result != "accepted":
            print(f"trace-lens hook: {result}", file=sys.stderr)
    except Exception as failure:
        category = "invalid_input" if isinstance(failure, (ValueError, json.JSONDecodeError)) else "internal_error"
        print(f"trace-lens hook: {category}", file=sys.stderr)
    # Hook 采集失败不能影响 Codex 的原命令执行；诊断信息仅写 stderr，始终正常退出。
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
