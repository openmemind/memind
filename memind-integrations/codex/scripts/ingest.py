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
from lib.agent_timeline import (
    build_timeline_payload,
    normalize_assistant_message_event,
    normalize_stop_event,
)
from lib.config import load_config
from lib.content import read_last_assistant_message
from lib.identity import resolve_identity
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.state import SessionStateStore, state_key


def state_root():
    override = os.environ.get("MEMIND_CODEX_STATE_ROOT")
    if override:
        return Path(override)
    return Path.home() / ".memind" / "codex" / "state"


def retry_root():
    override = os.environ.get("MEMIND_CODEX_RETRY_ROOT")
    if override:
        return Path(override)
    return Path.home() / ".memind" / "codex" / "retry"


def _spool_agent_timeline(retry_spool, identity, source_client, session_key, events, raw_content):
    if retry_spool is None or not events:
        return
    retry_spool.enqueue(
        {
            "kind": "extract",
            "userId": identity["userId"],
            "agentId": identity["agentId"],
            "sourceClient": source_client,
            "sessionKey": session_key,
            "eventIds": [event["eventId"] for event in events if event.get("eventId")],
            "rawContent": raw_content,
        }
    )


def _is_stop_hook(hook_input):
    return (hook_input.get("hook_event_name") or "") == "Stop"


def _append_stop_events(state, session_key, hook_input):
    if not _is_stop_hook(hook_input):
        return None
    turn_id, turn_seq = state.ensure_agent_turn(session_key)
    assistant_text = read_last_assistant_message(hook_input.get("transcript_path"))
    if assistant_text:
        seq = state.next_agent_seq()
        state.append_agent_event(
            normalize_assistant_message_event(
                hook_input, seq, turn_id=turn_id, turn_seq=turn_seq, text=assistant_text
            )
        )
    seq = state.next_agent_seq()
    state.append_agent_event(
        normalize_stop_event(hook_input, seq, turn_id=turn_id, turn_seq=turn_seq)
    )
    return turn_id


async def ingest_messages_async(config, hook_input):
    identity = resolve_identity(config, hook_input)
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10, max_retries=0)

    retry_spool = RetrySpool(retry_root()) if config.get("ingestRetrySpool", True) else None
    store = SessionStateStore(state_root())
    session_key = state_key(hook_input)
    source_client = config.get("sourceClient")
    agent_events_submitted = 0
    submitted_turn_id = None

    with store.locked(session_key) as state:
        if config.get("autoIngestAgentTimeline", True):
            hook_input["source_client"] = source_client or "codex"
            submitted_turn_id = _append_stop_events(state, session_key, hook_input)
            agent_events = state.agent_events()
        else:
            agent_events = []

    if agent_events:
        timeline_payload = build_timeline_payload(
            config,
            identity,
            session_key,
            agent_events,
            hook_input,
        )
        try:
            response = await client.extract(
                identity["userId"],
                identity["agentId"],
                timeline_payload,
                source_client,
            )
        except Exception:
            _spool_agent_timeline(
                retry_spool,
                identity,
                source_client,
                session_key,
                agent_events,
                timeline_payload,
            )
        else:
            status = getattr(response, "status", None)
            if status == "SUCCESS":
                agent_events_submitted = len(agent_events)
                with store.locked(session_key) as state:
                    state.clear_agent_events(
                        [event["eventId"] for event in agent_events if event.get("eventId")]
                    )
                    state.close_agent_turn(submitted_turn_id)
            else:
                _spool_agent_timeline(
                    retry_spool,
                    identity,
                    source_client,
                    session_key,
                    agent_events,
                    timeline_payload,
                )

    return {"agentEventsSubmitted": agent_events_submitted, "committed": False}


def ingest_messages(config, hook_input):
    return asyncio.run(ingest_messages_async(config, hook_input))


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
