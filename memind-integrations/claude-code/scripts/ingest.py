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
from lib.agent_timeline import build_timeline_payload
from lib.config import load_config
from lib.identity import resolve_identity
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.state import SessionStateStore


def state_root():
    override = os.environ.get("MEMIND_CLAUDE_STATE_ROOT")
    if override:
        return Path(override)
    return Path.home() / ".memind" / "claude-code" / "state"


def retry_root():
    override = os.environ.get("MEMIND_CLAUDE_RETRY_ROOT")
    if override:
        return Path(override)
    return Path.home() / ".memind" / "claude-code" / "retry"


def _spool_agent_timeline(retry_spool, identity, source_client, session_id, events, raw_content):
    if retry_spool is None or not events:
        return
    retry_spool.enqueue(
        {
            "kind": "extract",
            "userId": identity["userId"],
            "agentId": identity["agentId"],
            "sourceClient": source_client,
            "sessionId": session_id,
            "eventIds": [event["eventId"] for event in events if event.get("eventId")],
            "rawContent": raw_content,
        }
    )


async def ingest_messages_async(config, hook_input):
    identity = resolve_identity(config, hook_input)
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10, max_retries=0)
    retry_spool = RetrySpool(retry_root()) if config.get("ingestRetrySpool", True) else None
    store = SessionStateStore(state_root())
    session_id = hook_input.get("session_id") or "unknown-session"
    source_client = config.get("sourceClient")
    agent_events_submitted = 0
    with store.locked(session_id) as state:
        agent_events = state.agent_events() if config.get("autoIngestAgentTimeline", True) else []
        if agent_events:
            timeline_payload = build_timeline_payload(
                config,
                identity,
                session_id,
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
                    session_id,
                    agent_events,
                    timeline_payload,
                )
            else:
                status = getattr(response, "status", None)
                if status == "SUCCESS":
                    agent_events_submitted = len(agent_events)
                    state.clear_agent_events(
                        [event["eventId"] for event in agent_events if event.get("eventId")]
                    )
                else:
                    _spool_agent_timeline(
                        retry_spool,
                        identity,
                        source_client,
                        session_id,
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
