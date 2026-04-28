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
from lib.state import SessionStateStore


def state_root():
    return Path.home() / ".memind" / "claude-code" / "state"


def retry_root():
    return Path.home() / ".memind" / "claude-code" / "retry"


def ingest_messages(config, hook_input, commit=False, max_messages=None):
    identity = resolve_identity(config, hook_input)
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10)
    transcript_path = hook_input.get("transcript_path")
    messages = []
    if config.get("autoIngest", True) and transcript_path and Path(transcript_path).exists():
        messages = extract_messages(transcript_path, config.get("ingestionRoles", ["user", "assistant"]))
    limit = int(max_messages or config.get("ingestionMaxMessagesPerHook", 20))
    retry_spool = RetrySpool(retry_root()) if config.get("ingestRetrySpool", True) else None
    store = SessionStateStore(state_root())
    submitted = []
    session_id = hook_input.get("session_id") or "unknown-session"
    with store.locked(session_id) as state:
        new_messages = [message for message in messages if not state.is_submitted(message["fingerprint"])]
        for raw_message in new_messages[:limit]:
            fingerprint = raw_message["fingerprint"]
            message = {key: value for key, value in raw_message.items() if key != "fingerprint"}
            try:
                client.add_message(identity["userId"], identity["agentId"], message)
                submitted.append(fingerprint)
            except Exception:
                try:
                    time.sleep(0.5)
                    client.add_message(identity["userId"], identity["agentId"], message)
                    submitted.append(fingerprint)
                except Exception:
                    if retry_spool is not None:
                        retry_spool.enqueue(
                            {
                                "kind": "add-message",
                                "userId": identity["userId"],
                                "agentId": identity["agentId"],
                                "message": message,
                                "sessionId": session_id,
                                "fingerprint": fingerprint,
                            }
                        )
        state.mark_submitted(submitted)
    committed = False
    if commit:
        try:
            client.commit(identity["userId"], identity["agentId"])
            committed = True
        except Exception:
            if retry_spool is not None:
                retry_spool.enqueue({"kind": "commit", "userId": identity["userId"], "agentId": identity["agentId"]})
    return {"submitted": len(submitted), "committed": committed}


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
