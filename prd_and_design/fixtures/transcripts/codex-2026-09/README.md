# Codex transcript adapter fixture

This directory defines the synthetic public contract used by the `codex-2026-09` transcript adapter.
It contains no real Codex session, local path, account, or credential data.

The fixture covers:

- `session_meta.payload.session_id` file ownership;
- a user-visible message with a turn ID;
- a function call and output with a call ID;
- a visible reasoning summary;
- a final assistant-visible message.

Adapter changes must update the S2 specification and tests before implementation.
