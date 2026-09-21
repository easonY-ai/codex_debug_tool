import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from scripts import otel_sampler


class OtelSamplerTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.store = otel_sampler.CaptureStore(Path(self.temporary_directory.name))
        self.server = otel_sampler.OtelCaptureServer(
            ("127.0.0.1", 0),
            self.store,
            max_body_bytes=1024 * 1024,
            max_requests=0,
        )
        self.server_thread = threading.Thread(
            target=self.server.serve_forever,
            daemon=True,
        )
        self.server_thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.server_thread.join(timeout=2)
        self.temporary_directory.cleanup()

    def read_status(self):
        with urllib.request.urlopen(f"{self.base_url}/status", timeout=2) as response:
            return json.load(response)

    def post(self, signal, payload):
        request = urllib.request.Request(
            f"{self.base_url}/v1/{signal}",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        return urllib.request.urlopen(request, timeout=2)

    def test_status_keeps_idle_delivery_separate_from_unknown_coverage(self):
        status = self.read_status()

        self.assertEqual(status["receiverStatus"], "LISTENING")
        self.assertEqual(status["signals"]["logs"]["deliveryStatus"], "IDLE")
        self.assertIsNone(status["signals"]["logs"]["successRate"])
        self.assertEqual(status["signals"]["logs"]["coverageStatus"], "UNKNOWN")

    def test_status_reports_ready_failed_and_degraded_arrived_requests(self):
        valid_logs = json.dumps(
            {"resourceLogs": [{"scopeLogs": [{"logRecords": [{}]}]}]}
        ).encode()

        with self.post("logs", valid_logs) as response:
            self.assertEqual(response.status, 200)
        logs = self.read_status()["signals"]["logs"]
        self.assertEqual(logs["deliveryStatus"], "READY")
        self.assertEqual(logs["requests"], 1)
        self.assertEqual(logs["accepted"], 1)
        self.assertEqual(logs["failed"], 0)
        self.assertEqual(logs["successRate"], 1.0)
        self.assertEqual(logs["coverageStatus"], "UNKNOWN")

        with self.assertRaises(urllib.error.HTTPError) as invalid_response:
            self.post("traces", b"not-json")
        self.assertEqual(invalid_response.exception.code, 400)
        traces = self.read_status()["signals"]["traces"]
        self.assertEqual(traces["deliveryStatus"], "FAILED")
        self.assertEqual(traces["requests"], 1)
        self.assertEqual(traces["accepted"], 0)
        self.assertEqual(traces["failed"], 1)
        self.assertEqual(traces["successRate"], 0.0)

        with self.assertRaises(urllib.error.HTTPError):
            self.post("logs", b"not-json")
        logs = self.read_status()["signals"]["logs"]
        self.assertEqual(logs["deliveryStatus"], "DEGRADED")
        self.assertEqual(logs["requests"], 2)
        self.assertEqual(logs["accepted"], 1)
        self.assertEqual(logs["failed"], 1)
        self.assertEqual(logs["successRate"], 0.5)

    def test_official_contract_locks_exporter_configuration_boundaries(self):
        contract_path = (
            Path(__file__).resolve().parents[1]
            / "src/test/resources/otel/codex-official-otel-contract-2026-09-18.json"
        )
        contract = json.loads(contract_path.read_text())

        self.assertEqual(
            contract["configurationSemantics"],
            {
                "disabledByDefault": True,
                "noneExporterSendsNothing": True,
                "cliConfigOverrideSupported": True,
                "projectLocalOtelIgnored": True,
                "exportersBatchAsynchronously": True,
                "exportersFlushOnShutdown": True,
            },
        )

    def test_decoded_contract_summary_keeps_only_names_and_attribute_keys(self):
        decoded = """
        2 {
          6 { 1: "event.name" 2 { 1: "codex.user_prompt" } }
          6 { 1: "conversation.id" 2 { 1: "session-secret" } }
          6 { 1: "prompt" 2 { 1: "private prompt" } }
          6 { 1: "\\276\\001\\377private-id" 2 { 1: "binary-value" } }
        }
        2 {
          1: "codex.api_request.duration_ms"
          2: "Duration in milliseconds."
          3: "ms"
        }
        """

        summary = otel_sampler.summarize_decode_raw_text(decoded)

        self.assertEqual(
            summary["codexNames"],
            ["codex.api_request.duration_ms", "codex.user_prompt"],
        )
        self.assertEqual(
            summary["attributeKeys"],
            ["conversation.id", "event.name", "prompt"],
        )
        serialized = json.dumps(summary)
        self.assertNotIn("session-secret", serialized)
        self.assertNotIn("private prompt", serialized)
        self.assertNotIn("Duration in milliseconds", serialized)
        self.assertNotIn("private-id", serialized)

    def test_observed_contract_records_tool_turn_and_metric_evidence(self):
        contract_path = (
            Path(__file__).resolve().parents[1]
            / "src/test/resources/otel/codex-0.154.0-observed-contract.json"
        )
        contract = json.loads(contract_path.read_text())

        self.assertEqual(
            contract["sampleBatchCounts"],
            {"logs": 156, "traces": 65, "metrics": 16},
        )
        for signal in contract["signals"].values():
            self.assertEqual(signal["codexNames"], sorted(set(signal["codexNames"])))
            self.assertEqual(
                signal["attributeKeys"],
                sorted(set(signal["attributeKeys"])),
            )

        logs = contract["signals"]["logs"]
        self.assertTrue(
            {
                "codex.sse_event",
                "codex.tool_decision",
                "codex.tool_result",
                "codex.turn_ttft",
            }.issubset(logs["codexNames"])
        )
        self.assertTrue(
            {
                "call_id",
                "tool_name",
                "tool_namespace",
                "tool_result_seq",
                "ttft_ms",
            }.issubset(logs["attributeKeys"])
        )

        metrics = contract["signals"]["metrics"]["codexNames"]
        self.assertTrue(
            {
                "codex.hooks.run",
                "codex.tool.call",
                "codex.tool.call.duration_ms",
                "codex.turn.e2e_duration_ms",
                "codex.turn.tool.call",
                "codex.turn.ttft.duration_ms",
            }.issubset(metrics)
        )

    def test_merge_contract_summaries_deduplicates_sorted_names_and_keys(self):
        merged = otel_sampler.merge_contract_summaries(
            [
                {
                    "codexNames": ["codex.user_prompt"],
                    "attributeKeys": ["turn.id", "event.name"],
                },
                {
                    "codexNames": ["codex.api_request", "codex.user_prompt"],
                    "attributeKeys": ["conversation.id", "event.name"],
                },
            ]
        )

        self.assertEqual(
            merged["codexNames"],
            ["codex.api_request", "codex.user_prompt"],
        )
        self.assertEqual(
            merged["attributeKeys"],
            ["conversation.id", "event.name", "turn.id"],
        )

    def test_json_capture_keeps_raw_payload_and_redacts_header_values(self):
        payload = json.dumps(
            {
                "resourceSpans": [
                    {
                        "scopeSpans": [
                            {
                                "spans": [
                                    {
                                        "traceId": "trace-secret",
                                        "spanId": "span-secret",
                                        "name": "tool command with private text",
                                        "startTimeUnixNano": "1000000",
                                        "endTimeUnixNano": "3000000",
                                        "attributes": [
                                            {
                                                "key": "codex.call_id",
                                                "value": {"stringValue": "call-secret"},
                                            }
                                        ],
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        ).encode()

        with tempfile.TemporaryDirectory() as directory:
            store = otel_sampler.CaptureStore(Path(directory))
            result = store.capture(
                "traces",
                {
                    "Content-Type": "application/json",
                    "Authorization": "Bearer private-token",
                },
                payload,
            )

            self.assertEqual(result["signal"], "traces")
            self.assertEqual(result["objectCount"], 1)
            self.assertEqual(result["attributeKeys"], ["codex.call_id"])
            self.assertNotIn("trace-secret", json.dumps(result))
            self.assertNotIn("call-secret", json.dumps(result))
            self.assertEqual((Path(directory) / result["rawFile"]).read_bytes(), payload)

            manifest = json.loads(
                (Path(directory) / "manifest.jsonl").read_text().strip()
            )
            self.assertNotIn("authorization", manifest["headers"])
            self.assertEqual(manifest["headers"]["content-type"], "application/json")

    def test_binary_capture_writes_decode_raw_output(self):
        payload = b"\x0a\x03abc"

        with tempfile.TemporaryDirectory() as directory:
            store = otel_sampler.CaptureStore(
                Path(directory),
                protobuf_decoder=lambda body: '1: "abc"\n',
            )
            result = store.capture(
                "logs",
                {"Content-Type": "application/x-protobuf"},
                payload,
            )

            self.assertEqual(result["contentKind"], "protobuf")
            decoded = Path(directory) / result["decodedFile"]
            self.assertEqual(decoded.read_text(), '1: "abc"\n')

    def test_signal_path_only_accepts_supported_otlp_endpoints(self):
        self.assertEqual(otel_sampler.signal_from_path("/v1/logs"), "logs")
        self.assertEqual(otel_sampler.signal_from_path("/v1/traces"), "traces")
        self.assertEqual(otel_sampler.signal_from_path("/v1/metrics"), "metrics")
        self.assertIsNone(otel_sampler.signal_from_path("/api/other"))


if __name__ == "__main__":
    unittest.main()
