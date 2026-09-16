"""Safe, local runtime diagnostics for the CLI forwarder."""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timedelta
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path

LOG_PREFIX = "trace-lens-cli"
COMPONENT = "cli"


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        value = {
            "timestamp": datetime.now().astimezone().isoformat(timespec="milliseconds"),
            "level": record.levelname,
            "component": COMPONENT,
            "event": record.event,
            "outcome": record.outcome,
        }
        for key in ("httpStatus", "attempt", "durationMs", "errorCategory"):
            if hasattr(record, key):
                value[key] = getattr(record, key)
        return json.dumps(value, separators=(",", ":"))


def log_root() -> Path:
    return Path(os.environ.get("TRACE_LENS_LOG_ROOT", Path.home() / ".my_logs" / "codex_analyze"))


def cleanup_old_logs(directory: Path, today: datetime | None = None) -> None:
    cutoff = (today or datetime.now()).date() - timedelta(days=6)
    for path in directory.glob(f"{LOG_PREFIX}.log.*"):
        try:
            suffix = path.name.removeprefix(f"{LOG_PREFIX}.log.")
            if datetime.strptime(suffix, "%Y-%m-%d").date() < cutoff:
                path.unlink()
        except (OSError, ValueError):
            continue


def create_logger() -> logging.Logger:
    logger = logging.getLogger("trace_lens.cli")
    logger.handlers.clear()
    logger.propagate = False
    logger.setLevel(logging.INFO)
    try:
        directory = log_root() / "cli"
        directory.mkdir(parents=True, exist_ok=True)
        cleanup_old_logs(directory)
        handler = TimedRotatingFileHandler(directory / f"{LOG_PREFIX}.log", when="midnight", backupCount=6, encoding="utf-8")
        handler.setFormatter(JsonFormatter())
        logger.addHandler(handler)
    except OSError:
        logger.addHandler(logging.NullHandler())
    return logger


def record(logger: logging.Logger, event: str, outcome: str, **fields: int | str) -> None:
    try:
        logger.info(event, extra={"event": event, "outcome": outcome, **fields})
    except Exception:
        pass
