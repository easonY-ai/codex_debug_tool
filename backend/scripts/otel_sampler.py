#!/usr/bin/env python3
"""Capture local OTLP/HTTP payloads without involving the application database."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import threading
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable, Iterable, Mapping


SUPPORTED_SIGNALS = {"logs", "traces", "metrics"}
SAFE_HEADERS = {"content-type", "content-encoding"}
OBJECT_COLLECTIONS = {
    "logs": "logRecords",
    "traces": "spans",
    "metrics": "metrics",
}
IDENTIFIER_FIELDS = {
    "traceId",
    "spanId",
    "parentSpanId",
    "thread.id",
    "turn.id",
    "session.id",
    "conversation.id",
    "call_id",
    "tool_call_id",
}
QUOTED_PROTO_STRING = r'"((?:[^"\\]|\\.)*)"'
ATTRIBUTE_KEY_PATTERN = re.compile(
    rf'1:\s*{QUOTED_PROTO_STRING}\s*2\s*\{{',
    re.MULTILINE,
)
CODEX_NAME_PATTERN = re.compile(r'\bcodex\.[A-Za-z0-9_.-]+\b')
ATTRIBUTE_KEY_NAME_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_.-]*$")


def signal_from_path(path: str) -> str | None:
    prefix = "/v1/"
    signal = path.removeprefix(prefix) if path.startswith(prefix) else None
    return signal if signal in SUPPORTED_SIGNALS else None


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def iter_named_arrays(value: object, name: str) -> Iterable[list[object]]:
    if isinstance(value, dict):
        for key, child in value.items():
            if key == name and isinstance(child, list):
                yield child
            yield from iter_named_arrays(child, name)
    elif isinstance(value, list):
        for child in value:
            yield from iter_named_arrays(child, name)


def attribute_value(attribute: Mapping[str, object]) -> str | None:
    value = attribute.get("value")
    if not isinstance(value, dict):
        return None
    for field in (
        "stringValue",
        "intValue",
        "doubleValue",
        "boolValue",
        "bytesValue",
    ):
        if field in value:
            return str(value[field])
    return None


def summarize_json(signal: str, payload: object) -> dict[str, object]:
    collection = OBJECT_COLLECTIONS[signal]
    objects = [item for array in iter_named_arrays(payload, collection) for item in array]
    attribute_keys: set[str] = set()
    identifier_hashes: dict[str, set[str]] = {}
    field_names: set[str] = set()

    def inspect(value: object) -> None:
        if isinstance(value, dict):
            field_names.update(value.keys())
            attributes = value.get("attributes")
            if isinstance(attributes, list):
                for attribute in attributes:
                    if not isinstance(attribute, dict):
                        continue
                    key = attribute.get("key")
                    if not isinstance(key, str):
                        continue
                    attribute_keys.add(key)
                    raw_value = attribute_value(attribute)
                    if raw_value is not None and key in IDENTIFIER_FIELDS:
                        identifier_hashes.setdefault(key, set()).add(
                            sha256_text(raw_value)
                        )
            for key, child in value.items():
                if key in IDENTIFIER_FIELDS and isinstance(child, (str, int)):
                    identifier_hashes.setdefault(key, set()).add(
                        sha256_text(str(child))
                    )
                inspect(child)
        elif isinstance(value, list):
            for child in value:
                inspect(child)

    inspect(payload)
    return {
        "objectCount": len(objects),
        "fieldNames": sorted(field_names),
        "attributeKeys": sorted(attribute_keys),
        "identifierHashes": {
            key: sorted(values) for key, values in sorted(identifier_hashes.items())
        },
    }


def summarize_decode_raw_text(decoded: str) -> dict[str, list[str]]:
    """Extracts only contract names and keys from protoc decode_raw output."""
    codex_names = set(CODEX_NAME_PATTERN.findall(decoded))
    decoded_keys = (
        bytes(match, "utf-8").decode("unicode_escape")
        for match in ATTRIBUTE_KEY_PATTERN.findall(decoded)
    )
    attribute_keys = {
        key for key in decoded_keys if ATTRIBUTE_KEY_NAME_PATTERN.fullmatch(key)
    }
    return {
        "codexNames": sorted(codex_names),
        "attributeKeys": sorted(attribute_keys),
    }


def merge_contract_summaries(
    summaries: Iterable[Mapping[str, Iterable[str]]],
) -> dict[str, list[str]]:
    """Merges redacted contract summaries without retaining observed values."""
    codex_names: set[str] = set()
    attribute_keys: set[str] = set()
    for summary in summaries:
        codex_names.update(summary.get("codexNames", []))
        attribute_keys.update(summary.get("attributeKeys", []))
    return {
        "codexNames": sorted(codex_names),
        "attributeKeys": sorted(attribute_keys),
    }


def decode_protobuf_with_protoc(payload: bytes) -> str:
    protoc = shutil.which("protoc")
    if protoc is None:
        return "protoc unavailable; inspect the raw .pb file with an OTLP decoder.\n"
    completed = subprocess.run(
        [protoc, "--decode_raw"],
        input=payload,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
        timeout=15,
    )
    return completed.stdout.decode("utf-8", errors="replace")


class CaptureStore:
    def __init__(
        self,
        output_directory: Path,
        protobuf_decoder: Callable[[bytes], str] = decode_protobuf_with_protoc,
    ) -> None:
        self.output_directory = output_directory
        self.protobuf_decoder = protobuf_decoder
        self.output_directory.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def capture(
        self,
        signal: str,
        headers: Mapping[str, str],
        payload: bytes,
    ) -> dict[str, object]:
        if signal not in SUPPORTED_SIGNALS:
            raise ValueError(f"Unsupported OTLP signal: {signal}")

        request_id = uuid.uuid4().hex
        received_at = datetime.now(timezone.utc).isoformat(timespec="milliseconds")
        normalized_headers = {key.lower(): value for key, value in headers.items()}
        content_type = normalized_headers.get("content-type", "")
        is_json = "json" in content_type
        extension = "json" if is_json else "pb"
        raw_name = f"{request_id}.{signal}.{extension}"
        raw_path = self.output_directory / raw_name
        raw_path.write_bytes(payload)

        summary: dict[str, object] = {
            "requestId": request_id,
            "receivedAt": received_at,
            "signal": signal,
            "contentKind": "json" if is_json else "protobuf",
            "contentLength": len(payload),
            "contentSha256": hashlib.sha256(payload).hexdigest(),
            "rawFile": raw_name,
        }

        if is_json:
            parsed = json.loads(payload)
            summary.update(summarize_json(signal, parsed))
        else:
            decoded_name = f"{request_id}.{signal}.decoded.txt"
            (self.output_directory / decoded_name).write_text(
                self.protobuf_decoder(payload),
                encoding="utf-8",
            )
            summary["decodedFile"] = decoded_name

        summary_name = f"{request_id}.{signal}.summary.json"
        summary["summaryFile"] = summary_name
        (self.output_directory / summary_name).write_text(
            json.dumps(summary, ensure_ascii=True, indent=2) + "\n",
            encoding="utf-8",
        )

        manifest_entry = {
            **summary,
            "headers": {
                key: value
                for key, value in normalized_headers.items()
                if key in SAFE_HEADERS
            },
        }
        with self._lock:
            with (self.output_directory / "manifest.jsonl").open(
                "a", encoding="utf-8"
            ) as manifest:
                manifest.write(json.dumps(manifest_entry, ensure_ascii=True) + "\n")
        return summary


class OtelCaptureServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(
        self,
        address: tuple[str, int],
        store: CaptureStore,
        max_body_bytes: int,
        max_requests: int,
    ) -> None:
        super().__init__(address, OtelCaptureHandler)
        self.store = store
        self.max_body_bytes = max_body_bytes
        self.max_requests = max_requests
        self.captured_requests = 0
        self.capture_lock = threading.Lock()
        self.delivery_counts = {
            signal: {"requests": 0, "accepted": 0, "failed": 0}
            for signal in sorted(SUPPORTED_SIGNALS)
        }
        self.last_success_at: dict[str, str] = {}

    def record_delivery(self, signal: str, accepted: bool) -> None:
        with self.capture_lock:
            counts = self.delivery_counts[signal]
            counts["requests"] += 1
            outcome = "accepted" if accepted else "failed"
            counts[outcome] += 1
            if accepted:
                self.last_success_at[signal] = datetime.now(timezone.utc).isoformat(
                    timespec="milliseconds"
                )

    def status_snapshot(self) -> dict[str, object]:
        with self.capture_lock:
            signals: dict[str, object] = {}
            for signal, counts in self.delivery_counts.items():
                requests = counts["requests"]
                accepted = counts["accepted"]
                failed = counts["failed"]
                if requests == 0:
                    delivery_status = "IDLE"
                    success_rate = None
                elif accepted == requests:
                    delivery_status = "READY"
                    success_rate = 1.0
                elif accepted == 0:
                    delivery_status = "FAILED"
                    success_rate = 0.0
                else:
                    delivery_status = "DEGRADED"
                    success_rate = accepted / requests
                signals[signal] = {
                    "deliveryStatus": delivery_status,
                    "coverageStatus": "UNKNOWN",
                    "requests": requests,
                    "accepted": accepted,
                    "failed": failed,
                    "successRate": success_rate,
                    "lastSuccessAt": self.last_success_at.get(signal),
                }
        return {"receiverStatus": "LISTENING", "signals": signals}

    def captured(self) -> None:
        with self.capture_lock:
            self.captured_requests += 1
            should_stop = (
                self.max_requests > 0
                and self.captured_requests >= self.max_requests
            )
        if should_stop:
            threading.Thread(target=self.shutdown, daemon=True).start()


class OtelCaptureHandler(BaseHTTPRequestHandler):
    server: OtelCaptureServer

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path != "/status":
            self.send_error(404, "Unsupported sampler endpoint")
            return
        response = json.dumps(
            self.server.status_snapshot(),
            ensure_ascii=True,
        ).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        signal = signal_from_path(self.path)
        if signal is None:
            self.send_error(404, "Unsupported OTLP endpoint")
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.server.record_delivery(signal, accepted=False)
            self.send_error(400, "Invalid Content-Length")
            return
        if content_length <= 0:
            self.server.record_delivery(signal, accepted=False)
            self.send_error(400, "Empty OTLP request")
            return
        if content_length > self.server.max_body_bytes:
            self.server.record_delivery(signal, accepted=False)
            self.send_error(413, "OTLP request too large")
            return

        payload = self.rfile.read(content_length)
        try:
            result = self.server.store.capture(signal, dict(self.headers), payload)
        except (ValueError, json.JSONDecodeError):
            self.server.record_delivery(signal, accepted=False)
            self.send_error(400, "Invalid OTLP payload")
            return

        content_type = self.headers.get("Content-Type", "")
        if "json" in content_type:
            response = b'{"partialSuccess":{}}'
            response_type = "application/json"
        else:
            response = b""
            response_type = "application/x-protobuf"
        self.send_response(200)
        self.send_header("Content-Type", response_type)
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)
        self.server.record_delivery(signal, accepted=True)
        self.server.captured()
        print(json.dumps(result, ensure_ascii=True), flush=True)

    def log_message(self, format_string: str, *args: object) -> None:
        return


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture local OTLP/HTTP JSON or protobuf payloads."
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=4318)
    parser.add_argument("--output", type=Path, default=Path("otel-data"))
    parser.add_argument("--max-requests", type=int, default=0)
    parser.add_argument("--max-body-bytes", type=int, default=16 * 1024 * 1024)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.host not in {"127.0.0.1", "::1", "localhost"}:
        raise SystemExit("The sampler only binds to a loopback address.")
    store = CaptureStore(args.output)
    server = OtelCaptureServer(
        (args.host, args.port),
        store,
        args.max_body_bytes,
        args.max_requests,
    )
    print(
        f"OTLP sampler listening on http://{args.host}:{args.port}; "
        f"output={args.output}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
