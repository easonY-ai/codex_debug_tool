#!/usr/bin/env python3
"""Generate a Codex 0.154.0 hooks.json without editing user configuration."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

EVENTS = ["SessionStart", "SessionEnd", "SubagentStart", "PreToolUse", "PermissionRequest",
          "PostToolUse", "PreCompact", "PostCompact", "UserPromptSubmit", "SubagentStop", "Stop", "Interrupt"]


def config(command: str) -> dict:
    hooks = {}
    for event in EVENTS:
        timeout = 1 if event in {"SessionEnd", "Interrupt"} else 2
        hooks[event] = [{"hooks": [{"type": "command", "command": command, "timeout": timeout}]}]
    return {"description": "Trace Lens local Hook forwarder (Codex 0.154.0)", "hooks": hooks}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--command", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(config(args.command), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
