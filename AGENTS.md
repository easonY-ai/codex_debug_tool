# Repository Instructions

## Required context

Before planning or implementing any change, read `prd_and_design/README.md` in full and then read every document referenced by its document index. Treat those files as the persistent project context; do not rely on prior chat history to supply missing requirements.

Use the following precedence when resolving implementation questions:

1. `prd_and_design/01-product-requirements.md` defines product semantics, metric definitions and data boundaries.
2. `prd_and_design/prototype-v1` defines the approved V1 page structure, visual hierarchy and interactions.
3. `prd_and_design/03-data-and-correlation.md` defines JSONL, OTel and cross-source correlation rules.
4. `prd_and_design/02-technical-design.md` defines the engineering approach.

If these sources conflict or do not determine a material product decision, do not silently choose one. Explain the conflict and update the requirements before implementation.

## Protected design baseline

- `prd_and_design/prototype-v1` is a frozen, reproducible design baseline. Do not modify, reformat or regenerate files in that directory.
- Product or interaction changes must first update the PRD and prototype specification. If a new runnable prototype is required, create a new version such as `prd_and_design/prototype-v2`; never overwrite V1.
- Implement production frontend changes only in `frontend`.
- Implement backend changes only in `backend` after that directory and milestone are introduced.

## Documentation and repository safety

- Keep `prd_and_design` synchronized with approved changes to requirements, metric definitions, architecture, schemas and correlation behavior.
- This is a public repository. Never commit real account information, credentials, tokens, private keys, machine-specific paths, real Codex conversations or other sensitive data.
- Examples, fixtures and screenshots must use explicitly synthetic content such as `/workspace/demo-project` and fixed test identifiers.
- Do not commit dependency directories, generated build output, local databases, JSONL captures, logs or local configuration.
