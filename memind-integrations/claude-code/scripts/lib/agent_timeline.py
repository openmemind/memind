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


def event_id(source_client, session_id, seq, hook_input):
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
        },
        sort_keys=True,
    )
    return hashlib.sha256(stable.encode("utf-8")).hexdigest()


def normalize_hook_event(hook_input, seq):
    source_client = hook_input.get("source_client") or "claude-code"
    session_id = hook_input.get("session_id") or "unknown-session"
    tool_name = hook_input.get("tool_name")
    tool_input = hook_input.get("tool_input") or {}
    tool_response = hook_input.get("tool_response") or {}
    exit_code = tool_response.get("exit_code")
    redaction_kinds = []

    event = {
        "id": event_id(source_client, session_id, seq, hook_input),
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
        event["status"] = "success" if hook_input.get("hook_event_name") == "PostToolUse" else "started"

    output = {
        key: value
        for key, value in tool_response.items()
        if key not in {"exit_code", "exitCode"} and value is not None
    }
    if output:
        redacted_output, kinds = _redact_value(output)
        event["output"] = _json_text(redacted_output)
        redaction_kinds.extend(kinds)

    metadata = {
        "hookEventName": hook_input.get("hook_event_name"),
    }
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = {key: value for key, value in metadata.items() if value is not None}
    return {key: value for key, value in event.items() if value is not None}


def _event_kind(tool_name):
    if tool_name == "Bash":
        return "command"
    return "tool"


def append_event(state, event):
    state.append_agent_event(event)


def build_timeline_payload(config, identity, session_id, events, hook_input):
    source_client = config.get("sourceClient") or "claude-code"
    cwd = hook_input.get("cwd")
    first_seq = events[0].get("seq") if events else 0
    last_seq = events[-1].get("seq") if events else 0
    payload = {
        "type": "agent_timeline",
        "sourceClient": source_client,
        "sessionId": session_id,
        "timelineId": f"{session_id}-agent-{first_seq}-{last_seq}",
        "events": list(events),
        "metadata": {
            "userId": identity.get("userId"),
            "agentId": identity.get("agentId"),
        },
    }
    if cwd:
        payload["project"] = {"cwd": str(Path(cwd))}
    return payload
