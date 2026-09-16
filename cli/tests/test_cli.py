import io
import json
import pathlib
import sys
import tempfile
import unittest
from datetime import datetime
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import generate_hooks
import trace_lens_hook
import runtime_logging


class Response:
    def __init__(self, status): self.status = status
    def read(self): return b""


class Connection:
    statuses = []
    requests = []
    def __init__(self, *args, **kwargs): pass
    def request(self, *args, **kwargs): self.requests.append((args, kwargs))
    def getresponse(self): return Response(self.statuses.pop(0))
    def close(self): pass


class CliTest(unittest.TestCase):
    def setUp(self):
        Connection.statuses = []
        Connection.requests = []

    def test_envelope_preserves_raw_event(self):
        body = trace_lens_hook.envelope(b'{"session_id":"session-demo"}', 123, "delivery-demo")
        value = json.loads(body)
        self.assertEqual(value["rawEvent"], {"session_id": "session-demo"})
        self.assertEqual(value["observedAt"], 123)

    def test_retries_5xx_once(self):
        Connection.statuses = [503, 202]
        self.assertEqual(trace_lens_hook.deliver(b"{}", Connection, lambda _: None), "accepted")
        self.assertEqual(len(Connection.requests), 2)

    def test_every_failure_exits_zero_without_raw_input(self):
        fake_stdin = type("Stdin", (), {"buffer": io.BytesIO(b"not-json secret-demo")})()
        error = io.StringIO()
        with patch("sys.stdin", fake_stdin), patch("sys.stderr", error):
            self.assertEqual(trace_lens_hook.main(), 0)
        self.assertNotIn("secret-demo", error.getvalue())

    def test_runtime_log_is_structured_and_does_not_contain_raw_input(self):
        with tempfile.TemporaryDirectory() as temporary:
            with patch.dict("os.environ", {"TRACE_LENS_LOG_ROOT": temporary}):
                fake_stdin = type("Stdin", (), {"buffer": io.BytesIO(b"not-json secret-demo")})()
                with patch("sys.stdin", fake_stdin), patch("sys.stderr", io.StringIO()):
                    self.assertEqual(trace_lens_hook.main(), 0)
            content = (pathlib.Path(temporary) / "cli" / "trace-lens-cli.log").read_text()
        value = json.loads(content)
        self.assertEqual(value["event"], "hook_forward_rejected_input")
        self.assertEqual(value["outcome"], "invalid_input")
        self.assertNotIn("secret-demo", content)

    def test_cleanup_keeps_only_last_seven_days_for_its_own_prefix(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = pathlib.Path(temporary)
            (directory / "trace-lens-cli.log.2026-09-08").touch()
            retained = directory / "trace-lens-cli.log.2026-09-10"
            retained.touch()
            unrelated = directory / "other.log.2026-09-01"
            unrelated.touch()
            runtime_logging.cleanup_old_logs(directory, datetime(2026, 9, 16))
            self.assertFalse((directory / "trace-lens-cli.log.2026-09-08").exists())
            self.assertTrue(retained.exists())
            self.assertTrue(unrelated.exists())

    def test_config_covers_every_official_event(self):
        value = generate_hooks.config("/workspace/demo-project/bin/trace-lens-hook")
        self.assertEqual(set(value["hooks"]), set(generate_hooks.EVENTS))
        self.assertEqual(value["hooks"]["Interrupt"][0]["hooks"][0]["timeout"], 1)


if __name__ == "__main__": unittest.main()
