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

import hashlib
import json
from datetime import datetime, timezone

from memind_hermes.content import clamp_text


def user_prompt_event(text, seq, *, session_id, max_field_chars=8000, metadata=None):
    cleaned = clamp_text(text, max_field_chars)
    if not cleaned:
        return None
    return _event("user_prompt", seq, session_id, text=cleaned, metadata=metadata)


def assistant_message_event(text, seq, *, session_id, max_field_chars=8000, metadata=None):
    cleaned = clamp_text(text, max_field_chars)
    if not cleaned:
        return None
    return _event("assistant_message", seq, session_id, text=cleaned, metadata=metadata)


def memory_write_event(action, target, content, seq, *, session_id, max_field_chars=8000):
    cleaned = clamp_text(content, max_field_chars)
    if not cleaned:
        return None
    return _event(
        "notification",
        seq,
        session_id,
        text=cleaned,
        metadata={
            "memoryWrite": True,
            "action": action,
            "target": target,
        },
    )


def compact_boundary_event(messages, seq, *, session_id):
    return _event(
        "compact_boundary",
        seq,
        session_id,
        status="success",
        metadata={"messageCount": len(messages or [])},
    )


def stop_event(seq, *, session_id, success=True):
    return _event("stop", seq, session_id, status="success" if success else "failed")


def session_end_event(seq, *, session_id, metadata=None):
    return _event("session_end", seq, session_id, status="success", metadata=metadata)


def build_timeline_content(
    *,
    source_client,
    session_id,
    agent_turn_id,
    timeline_id,
    events,
    metadata=None,
):
    metadata = _compact_metadata(
        {
            "profile": "general",
            "runtime": "hermes",
            "sessionKey": session_id,
            **(metadata or {}),
        }
    )
    return {
        "type": "agent_timeline",
        "sourceClient": source_client,
        "sessionId": session_id,
        "agentTurnId": agent_turn_id,
        "timelineId": timeline_id,
        "events": [event for event in events if event],
        "metadata": metadata,
    }


def _event(kind, seq, session_id, *, text=None, status=None, metadata=None):
    payload = {
        "kind": kind,
        "seq": seq,
        "sessionId": session_id,
        "text": text,
        "status": status,
        "metadata": metadata or {},
    }
    content_hash = _hash(payload)
    event = {
        "eventId": f"hermes-{session_id}-{seq}-{content_hash[:12]}",
        "seq": seq,
        "kind": kind,
        "occurredAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "contentHash": content_hash,
        "metadata": _compact_metadata({"sessionKey": session_id, **(metadata or {})}),
    }
    if text:
        event["text"] = text
    if status:
        event["status"] = status
    return event


def _hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, default=str).encode("utf-8")).hexdigest()


def _compact_metadata(values):
    result = {}
    for key, value in values.items():
        if isinstance(value, str) and value.strip():
            result[key] = value
        elif isinstance(value, (int, float, bool)) and value is not None:
            result[key] = value
    return result
