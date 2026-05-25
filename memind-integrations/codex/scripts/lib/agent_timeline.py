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
import re
from pathlib import Path


MAX_TEXT_CHARS = 4000

SECRET_PATTERNS = [
    ("openai_key", re.compile(r"sk-[A-Za-z0-9_-]{8,}")),
    ("bearer_token", re.compile(r"Bearer\s+[A-Za-z0-9._~+/=-]+", re.IGNORECASE)),
    ("private_key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", re.DOTALL)),
]


def redact_text(text):
    redacted = str(text)
    kinds = []
    for kind, pattern in SECRET_PATTERNS:
        if pattern.search(redacted):
            redacted = pattern.sub(f"[REDACTED:{kind}]", redacted)
            kinds.append(kind)
    if len(redacted) > MAX_TEXT_CHARS:
        redacted = redacted[:MAX_TEXT_CHARS]
        kinds.append("truncated")
    return redacted, sorted(set(kinds))


def _redact_value(value):
    if value is None or isinstance(value, (bool, int, float)):
        return value, []
    if isinstance(value, str):
        return redact_text(value)
    if isinstance(value, list):
        result = []
        kinds = []
        for item in value:
            redacted, item_kinds = _redact_value(item)
            result.append(redacted)
            kinds.extend(item_kinds)
        return result, sorted(set(kinds))
    if isinstance(value, dict):
        result = {}
        kinds = []
        for key, item in value.items():
            redacted, item_kinds = _redact_value(item)
            result[key] = redacted
            kinds.extend(item_kinds)
        return result, sorted(set(kinds))
    redacted, kinds = redact_text(value)
    return redacted, kinds


def _json_text(value):
    if value is None or isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def event_id(source_client, session_id, seq, hook_input, kind=None, text=None):
    hook_name = hook_input.get("hook_event_name") or ""
    tool_name = hook_input.get("tool_name") or ""
    timestamp = hook_input.get("timestamp") or ""
    stable = json.dumps(
        {
            "sourceClient": source_client,
            "sessionId": session_id,
            "seq": seq,
            "hook": hook_name,
            "tool": tool_name,
            "timestamp": timestamp,
            "kind": kind or "",
            "textHash": hashlib.sha256((text or "").encode("utf-8")).hexdigest(),
        },
        sort_keys=True,
    )
    return hashlib.sha256(stable.encode("utf-8")).hexdigest()


def _base_event(hook_input, seq, kind, turn_id=None, turn_seq=None, text=None):
    source_client = hook_input.get("source_client") or "codex"
    session_id = hook_input.get("session_id") or "unknown-session"
    metadata = {"hookEventName": hook_input.get("hook_event_name")}
    if turn_id:
        metadata["turnId"] = turn_id
    if turn_seq is not None:
        metadata["turnSeq"] = turn_seq
    return {
        "eventId": event_id(source_client, session_id, seq, hook_input, kind, text),
        "seq": seq,
        "kind": kind,
        "occurredAt": hook_input.get("timestamp"),
        "metadata": {key: value for key, value in metadata.items() if value is not None},
    }


def normalize_user_prompt_event(hook_input, seq, turn_id=None, turn_seq=None):
    text, redaction_kinds = redact_text(hook_input.get("prompt") or hook_input.get("user_prompt") or "")
    event = _base_event(hook_input, seq, "user_prompt", turn_id, turn_seq, text)
    event["text"] = text
    event["status"] = "success"
    if redaction_kinds:
        metadata = dict(event["metadata"])
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
        event["metadata"] = metadata
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_assistant_message_event(hook_input, seq, turn_id=None, turn_seq=None, text=None):
    redacted, redaction_kinds = redact_text(text or "")
    event = _base_event(hook_input, seq, "assistant_message", turn_id, turn_seq, redacted)
    event["text"] = redacted
    event["status"] = "success"
    if redaction_kinds:
        metadata = dict(event["metadata"])
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
        event["metadata"] = metadata
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_stop_event(hook_input, seq, turn_id=None, turn_seq=None):
    text = hook_input.get("reason") or hook_input.get("stop_reason") or ""
    event = _base_event(hook_input, seq, "stop", turn_id, turn_seq, text)
    if text:
        redacted, redaction_kinds = redact_text(text)
        event["text"] = redacted
        if redaction_kinds:
            metadata = dict(event["metadata"])
            metadata["redacted"] = True
            metadata["redactionKinds"] = sorted(set(redaction_kinds))
            event["metadata"] = metadata
    event["status"] = "success"
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_hook_event(hook_input, seq, turn_id=None, turn_seq=None):
    source_client = hook_input.get("source_client") or "codex"
    session_id = hook_input.get("session_id") or "unknown-session"
    tool_name = hook_input.get("tool_name")
    tool_input = hook_input.get("tool_input") or {}
    tool_response = hook_input.get("tool_response") or {}
    exit_code = tool_response.get("exit_code")
    redaction_kinds = []

    event = {
        "eventId": event_id(source_client, session_id, seq, hook_input, _event_kind(tool_name)),
        "seq": seq,
        "kind": _event_kind(tool_name),
        "occurredAt": hook_input.get("timestamp"),
        "toolName": tool_name,
    }
    if tool_name == "Bash" and isinstance(tool_input, dict):
        command, kinds = redact_text(tool_input.get("command") or "")
        event["command"] = command
        redaction_kinds.extend(kinds)
    else:
        redacted_input, kinds = _redact_value(tool_input)
        event["input"] = _json_text(redacted_input)
        redaction_kinds.extend(kinds)

    if exit_code is not None:
        event["exitCode"] = exit_code
        event["status"] = "success" if exit_code == 0 else "failed"
    else:
        event["status"] = "success" if hook_input.get("hook_event_name") == "PostToolUse" else "running"

    output = {
        key: value
        for key, value in tool_response.items()
        if key not in {"exit_code", "exitCode"} and value is not None
    }
    if output:
        redacted_output, kinds = _redact_value(output)
        event["output"] = _json_text(redacted_output)
        redaction_kinds.extend(kinds)

    metadata = {"hookEventName": hook_input.get("hook_event_name")}
    if turn_id:
        metadata["turnId"] = turn_id
    if turn_seq is not None:
        metadata["turnSeq"] = turn_seq
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = {key: value for key, value in metadata.items() if value is not None}
    return {key: value for key, value in event.items() if value is not None}


def _event_kind(tool_name):
    if tool_name == "Bash":
        return "command"
    return "tool_result"


def append_event(state, event):
    state.append_agent_event(event)


def build_timeline_payload(config, identity, session_id, events, hook_input):
    source_client = config.get("sourceClient") or "codex"
    cwd = hook_input.get("cwd")
    first_seq = events[0].get("seq") if events else 0
    last_seq = events[-1].get("seq") if events else 0
    turn_id = _shared_metadata(events, "turnId")
    turn_seq = _shared_metadata(events, "turnSeq")
    agent_turn_id = turn_id or f"{session_id}-agent-turn-{first_seq}-{last_seq}"
    payload = {
        "type": "agent_timeline",
        "sourceClient": source_client,
        "sessionId": session_id,
        "agentTurnId": agent_turn_id,
        "timelineId": f"{agent_turn_id}-timeline",
        "events": list(events),
        "metadata": {
            "userId": identity.get("userId"),
            "agentId": identity.get("agentId"),
            "eventIds": [event["eventId"] for event in events if event.get("eventId")],
        },
    }
    if turn_id:
        payload["metadata"]["turnId"] = turn_id
    if turn_seq is not None:
        payload["metadata"]["turnSeq"] = turn_seq
    if cwd:
        path = Path(cwd)
        payload["project"] = {"name": path.name, "rootPath": str(path)}
    return payload


def _shared_metadata(events, key):
    values = []
    for event in events:
        metadata = event.get("metadata") or {}
        value = metadata.get(key)
        if value is not None:
            values.append(value)
    if len(set(values)) == 1:
        return values[0]
    return None
