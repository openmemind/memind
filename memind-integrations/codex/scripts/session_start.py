#!/usr/bin/env python3

import json
import os
import socket
import sys
from urllib.parse import urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ingest import retry_root, state_root
from lib.client import MemindClient
from lib.config import load_config
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.state import SessionStateStore


def _tcp_check(url, timeout=1):
    parsed = urlparse(url)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    with socket.create_connection((host, port), timeout=timeout):
        return True


def _replay_ingestion_batch(client, payload):
    session_key = payload.get("sessionKey")
    fingerprints = payload.get("fingerprints") or []
    operations = payload.get("operations") or []
    store = SessionStateStore(state_root())
    appended = 0
    for index, operation in enumerate(operations):
        if operation.get("kind") != "add-message":
            continue
        fingerprint = fingerprints[index] if index < len(fingerprints) else None
        if fingerprint and session_key:
            with store.locked(session_key) as state:
                if state.is_submitted(fingerprint):
                    continue
        client.add_message(
            operation["userId"],
            operation["agentId"],
            operation["message"],
            operation.get("sourceClient"),
        )
        appended += 1
        if fingerprint and session_key:
            store.mark_submitted(session_key, [fingerprint])
    if payload.get("commitOnSuccess"):
        client.commit(payload["userId"], payload["agentId"], payload.get("sourceClient"))
    return appended


def _replay_payload(client, payload):
    kind = payload.get("kind")
    if kind == "ingestion-batch":
        return _replay_ingestion_batch(client, payload)
    if kind == "commit":
        client.commit(payload["userId"], payload["agentId"], payload.get("sourceClient"))
        return 0
    return 0


def run_session_start(config):
    try:
        client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=2)
        try:
            client.health()
        except Exception:
            _tcp_check(config["memindApiUrl"], timeout=1)
    except Exception as exc:
        debug_log(config, "session_start_health_failed", {"error": str(exc)})
        return

    spool = RetrySpool(retry_root())
    claimed = None
    try:
        claimed = spool.claim_next()
        if claimed:
            payload = spool.load_claimed(claimed)
            replay_client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10)
            _replay_payload(replay_client, payload)
            spool.complete(claimed)
        spool.cleanup(int(config.get("ingestRetryMaxFiles", 20)), int(config.get("ingestRetryMaxAgeDays", 7)))
    except Exception as exc:
        try:
            if claimed:
                spool.release(claimed)
        except Exception:
            pass
        debug_log(config, "retry_replay_failed", {"error": str(exc)})

    try:
        SessionStateStore(state_root()).cleanup(int(config.get("stateMaxAgeDays", 14)))
    except Exception as exc:
        debug_log(config, "state_cleanup_failed", {"error": str(exc)})


def main():
    try:
        config = load_config()
        run_session_start(config)
    except Exception as exc:
        try:
            debug_log(load_config(), "session_start_failed", {"error": str(exc)})
        except Exception:
            pass
    print(json.dumps({"continue": True, "suppressOutput": True}))


if __name__ == "__main__":
    main()
