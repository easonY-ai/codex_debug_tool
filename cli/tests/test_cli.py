import io
import json
import pathlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import generate_hooks
import trace_lens_hook


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

    def test_config_covers_every_official_event(self):
        value = generate_hooks.config("/workspace/demo-project/bin/trace-lens-hook")
        self.assertEqual(set(value["hooks"]), set(generate_hooks.EVENTS))
        self.assertEqual(value["hooks"]["Interrupt"][0]["hooks"][0]["timeout"], 1)


if __name__ == "__main__": unittest.main()
