#!/usr/bin/env python3

import json
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib.client import MemindClient
from lib.config import load_config
from lib.content import extract_messages
from lib.identity import resolve_identity
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.state import SessionStateStore, state_key


def state_root():
    return Path.home() / ".memind" / "codex" / "state"


def retry_root():
    return Path.home() / ".memind" / "codex" / "retry"


def _message_payload(raw_message):
    return {key: value for key, value in raw_message.items() if key != "fingerprint"}


def _operation(user_id, agent_id, message, source_client):
    return {
        "kind": "add-message",
        "userId": user_id,
        "agentId": agent_id,
        "sourceClient": source_client,
        "message": message,
    }


def _spool_batch(retry_spool, identity, source_client, session_key, messages, commit_on_success):
    if retry_spool is None or not messages:
        return
    retry_spool.enqueue(
        {
            "kind": "ingestion-batch",
            "userId": identity["userId"],
            "agentId": identity["agentId"],
            "sourceClient": source_client,
            "operations": [
                _operation(identity["userId"], identity["agentId"], _message_payload(message), source_client)
                for message in messages
            ],
            "sessionKey": session_key,
            "fingerprints": [message["fingerprint"] for message in messages],
            "commitOnSuccess": bool(commit_on_success),
        }
    )


def _add_message_with_retry(client, user_id, agent_id, message, source_client):
    try:
        client.add_message(user_id, agent_id, message, source_client)
        return True
    except Exception:
        time.sleep(0.5)
        client.add_message(user_id, agent_id, message, source_client)
        return True


def ingest_messages(config, hook_input):
    identity = resolve_identity(config, hook_input)
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10)
    transcript_path = hook_input.get("transcript_path")
    messages = []
    if config.get("autoIngest", True) and transcript_path and Path(transcript_path).exists():
        messages = extract_messages(transcript_path, config.get("ingestionRoles", ["user", "assistant"]))

    limit = int(config.get("ingestionMaxMessagesPerHook", 20))
    retry_spool = RetrySpool(retry_root()) if config.get("ingestRetrySpool", True) else None
    store = SessionStateStore(state_root())
    session_key = state_key(hook_input)
    source_client = config.get("sourceClient")
    commit_on_stop = bool(config.get("commitOnStop", False))

    with store.locked(session_key) as state:
        selected = [message for message in messages if not state.is_submitted(message["fingerprint"])][:limit]

    submitted = []
    failed = False
    for index, raw_message in enumerate(selected):
        fingerprint = raw_message["fingerprint"]
        with store.locked(session_key) as state:
            if state.is_submitted(fingerprint):
                continue
        message = _message_payload(raw_message)
        try:
            _add_message_with_retry(client, identity["userId"], identity["agentId"], message, source_client)
            store.mark_submitted(session_key, [fingerprint])
            submitted.append(fingerprint)
        except Exception:
            failed = True
            _spool_batch(
                retry_spool,
                identity,
                source_client,
                session_key,
                selected[index:],
                commit_on_success=commit_on_stop,
            )
            break

    committed = False
    if commit_on_stop and submitted and not failed:
        try:
            client.commit(identity["userId"], identity["agentId"], source_client)
            committed = True
        except Exception:
            if retry_spool is not None:
                retry_spool.enqueue(
                    {
                        "kind": "commit",
                        "userId": identity["userId"],
                        "agentId": identity["agentId"],
                        "sourceClient": source_client,
                    }
                )
    return {"submitted": len(submitted), "committed": committed}


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        config = load_config()
        ingest_messages(config, hook_input)
    except Exception as exc:
        try:
            debug_log(load_config(), "ingest_failed", {"error": str(exc)})
        except Exception:
            pass
    print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
