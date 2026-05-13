#!/usr/bin/env python3
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import asyncio
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib.client import MemindClient
from lib.config import load_config
from lib.content import extract_messages
from lib.identity import resolve_identity
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.state import SessionStateStore


def state_root():
    return Path.home() / ".memind" / "claude-code" / "state"


def retry_root():
    return Path.home() / ".memind" / "claude-code" / "retry"


def _message_payload(raw_message):
    return {key: value for key, value in raw_message.items() if key != "fingerprint"}


def _extract_payload(messages):
    return {
        "type": "conversation",
        "messages": [_message_payload(message) for message in messages],
    }


def _spool_extract(retry_spool, identity, source_client, session_id, messages):
    if retry_spool is None or not messages:
        return
    retry_spool.enqueue(
        {
            "kind": "extract",
            "userId": identity["userId"],
            "agentId": identity["agentId"],
            "sourceClient": source_client,
            "sessionId": session_id,
            "fingerprints": [message["fingerprint"] for message in messages],
            "rawContent": _extract_payload(messages),
        }
    )


async def ingest_messages_async(config, hook_input, commit=False, max_messages=None):
    identity = resolve_identity(config, hook_input)
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10, max_retries=0)
    transcript_path = hook_input.get("transcript_path")
    messages = []
    if config.get("autoIngest", True) and transcript_path and Path(transcript_path).exists():
        messages = extract_messages(transcript_path, config.get("ingestionRoles", ["user", "assistant"]))
    limit = int(max_messages or config.get("ingestionMaxMessagesPerHook", 20))
    retry_spool = RetrySpool(retry_root()) if config.get("ingestRetrySpool", True) else None
    store = SessionStateStore(state_root())
    submitted = []
    session_id = hook_input.get("session_id") or "unknown-session"
    source_client = config.get("sourceClient")
    with store.locked(session_id) as state:
        new_messages = [message for message in messages if not state.is_submitted(message["fingerprint"])]
        selected = new_messages[:limit]
        if selected:
            response = None
            try:
                response = await client.extract(
                    identity["userId"],
                    identity["agentId"],
                    _extract_payload(selected),
                    source_client,
                )
            except Exception:
                _spool_extract(retry_spool, identity, source_client, session_id, selected)
            else:
                status = getattr(response, "status", None)
                if status == "SUCCESS":
                    submitted = [message["fingerprint"] for message in selected]
                else:
                    _spool_extract(retry_spool, identity, source_client, session_id, selected)
        state.mark_submitted(submitted)
    committed = False
    return {"submitted": len(submitted), "committed": committed}


def ingest_messages(config, hook_input, commit=False, max_messages=None):
    return asyncio.run(ingest_messages_async(config, hook_input, commit=commit, max_messages=max_messages))


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        config = load_config()
        ingest_messages(config, hook_input, commit=False)
    except Exception as exc:
        try:
            debug_log(load_config(), "ingest_failed", {"error": str(exc)})
        except Exception:
            pass
    print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
