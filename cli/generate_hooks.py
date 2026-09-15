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
        # Hook 命令在 Codex 的交互路径上运行；退出/中断事件只需尽力采集，
        # 因此使用更短超时，避免会话结束时因本地服务暂不可用而额外等待。
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
