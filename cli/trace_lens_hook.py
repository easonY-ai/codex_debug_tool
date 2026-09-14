#!/usr/bin/env python3
"""Non-blocking Codex Hook forwarder. Uses only Python's standard library."""

from __future__ import annotations

import json
import sys
import time
import uuid
from http.client import HTTPConnection

VERSION = "0.1.0"
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
            sleeper(0.05)
    return "unavailable"


def main() -> int:
    try:
        raw = sys.stdin.buffer.read(MAX_STDIN_BYTES + 1)
        result = deliver(envelope(raw))
        if result != "accepted":
            print(f"trace-lens hook: {result}", file=sys.stderr)
    except Exception as failure:
        category = "invalid_input" if isinstance(failure, (ValueError, json.JSONDecodeError)) else "internal_error"
        print(f"trace-lens hook: {category}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
