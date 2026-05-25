# rawdata-agent Evaluation Fixtures

This directory contains small black-box fixtures for validating Memind's
`agent_timeline` ingestion path against a running `memind-server`.

Each fixture is a self-contained JSON document:

- `request`: payload for `POST /open/v1/memory/sync/extract`.
- `expectations.expectedCategories`: item categories expected from the first extraction.
- `expectations.queries`: retrieval checks for `POST /open/v1/memory/retrieve`.
- `expectations.optionalCategories`: categories that may appear when LLM extraction is enabled,
  but are not required for deterministic v1 acceptance.

The fixtures are intentionally deterministic-friendly. They validate the core
coding-agent memory path without depending on an LLM:

- command/tool reuse memory, such as `npm test payment`.
- resolution memory from a failed command, file edit, and later matching success.
- duplicate submission behavior through stable raw content and item hashes.

## Run

Start `memind-server` on the default port, then run:

```bash
python3 evaluation/rawdata-agent/run-fixtures.py --base-url http://127.0.0.1:8366
```

The runner exits non-zero when the server is unreachable or any fixture fails.
It prints a duplicate item rate for each fixture so regressions in idempotency
are visible during manual evaluation.
