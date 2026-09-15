#!/usr/bin/env python3
"""Run integration tests against the pre-created local codex_analyze_test schema.

Uses inherited MYSQL_USERNAME and MYSQL_PASSWORD. Never starts MySQL or creates tables.
The Java test base verifies the connected schema before fixture cleanup.
"""
import os
from pathlib import Path
import subprocess
import sys


def main():
    environment = os.environ.copy()
    if not all(environment.get(key) for key in ("MYSQL_USERNAME", "MYSQL_PASSWORD")):
        raise SystemExit("Set MYSQL_USERNAME and MYSQL_PASSWORD locally before testing.")
    command = sys.argv[1:]
    if command[:1] == ["--"]:
        command = command[1:]
    command = command or ["./mvnw", "-B", "-ntp", "clean", "verify"]
    environment["TRACE_LENS_MYSQL_TEST"] = "1"
    print("Using existing local codex_analyze_test schema; fixture data will be cleared.", flush=True)
    return subprocess.call(command, cwd=Path(__file__).resolve().parents[1], env=environment)


if __name__ == "__main__":
    sys.exit(main())
