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

try:
    from .identity import project_slug
except ImportError:
    from lib.identity import project_slug


MAX_TEXT_CHARS = 4000
NORMALIZATION_VERSION = 1

SECRET_PATTERNS = [
    ("openai_key", re.compile(r"sk-[A-Za-z0-9_-]{8,}")),
    ("bearer_token", re.compile(r"Bearer\s+[A-Za-z0-9._~+/=-]+", re.IGNORECASE)),
    ("private_key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", re.DOTALL)),
]

PATH_KEYS = [
    "file_path",
    "filepath",
    "filePath",
    "path",
    "file",
    "target_file",
    "targetFile",
    "target_path",
    "targetPath",
    "notebook_path",
    "notebookPath",
]
PATH_LIST_KEYS = ["files", "paths"]
COMMAND_KEYS = ["command", "cmd", "shell_command"]
SEARCH_PATTERN_KEYS = ["pattern", "query", "regex", "glob"]
URL_KEYS = ["url", "uri", "href"]

TEST_COMMAND_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"(^|[\s;&|])(?:npm|pnpm|yarn|bun)\s+(?:run\s+)?(?:test|vitest|jest)(?:\b|:)",
        r"(^|[\s;&|])pytest\b",
        r"(^|[\s;&|])python(?:3)?\s+-m\s+unittest\b",
        r"(^|[\s;&|])go\s+test\b",
        r"(^|[\s;&|])cargo\s+test\b",
        r"(^|[\s;&|])mvn\b.*\b(?:test|verify)\b",
        r"(^|[\s;&|])(?:gradle|gradlew|./gradlew)\b.*\btest\b",
        r"(^|[\s;&|])(?:vitest|jest|mocha|ctest|rspec)\b",
    ]
]

LINT_COMMAND_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"(^|[\s;&|])(?:eslint|ruff|pylint|flake8|checkstyle)\b",
        r"\b(?:lint|spotless:check|license:check)\b",
    ]
]

TYPECHECK_COMMAND_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"\b(?:typecheck|type-check|tsc\s+--noEmit|mypy|pyright)\b",
    ]
]

BUILD_COMMAND_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"(^|[\s;&|])(?:npm|pnpm|yarn|bun)\s+(?:run\s+)?build\b",
        r"(^|[\s;&|])mvn\b.*\b(?:compile|package|install)\b",
        r"(^|[\s;&|])cargo\s+(?:build|check)\b",
        r"(^|[\s;&|])go\s+build\b",
    ]
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


def _number_value(*values):
    for value in values:
        if isinstance(value, bool):
            continue
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        if isinstance(value, str) and value.strip().isdigit():
            return int(value.strip())
    return None


def _nested_number(mapping, *path):
    current = mapping
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return _number_value(current)


def _tool_telemetry(hook_input, tool_response):
    usage = tool_response.get("usage") if isinstance(tool_response, dict) else {}
    return {
        "durationMs": _number_value(
            hook_input.get("duration_ms"),
            hook_input.get("durationMs"),
            tool_response.get("duration_ms") if isinstance(tool_response, dict) else None,
            tool_response.get("durationMs") if isinstance(tool_response, dict) else None,
            _nested_number(tool_response, "metadata", "duration_ms"),
            _nested_number(tool_response, "metadata", "durationMs"),
        ),
        "inputTokens": _number_value(
            hook_input.get("input_tokens"),
            hook_input.get("inputTokens"),
            tool_response.get("input_tokens") if isinstance(tool_response, dict) else None,
            tool_response.get("inputTokens") if isinstance(tool_response, dict) else None,
            usage.get("input_tokens") if isinstance(usage, dict) else None,
            usage.get("inputTokens") if isinstance(usage, dict) else None,
        ),
        "outputTokens": _number_value(
            hook_input.get("output_tokens"),
            hook_input.get("outputTokens"),
            tool_response.get("output_tokens") if isinstance(tool_response, dict) else None,
            tool_response.get("outputTokens") if isinstance(tool_response, dict) else None,
            usage.get("output_tokens") if isinstance(usage, dict) else None,
            usage.get("outputTokens") if isinstance(usage, dict) else None,
        ),
    }


VOLATILE_TOOL_OUTPUT_KEYS = {
    "exit_code",
    "exitCode",
    "duration_ms",
    "durationMs",
    "input_tokens",
    "inputTokens",
    "output_tokens",
    "outputTokens",
    "usage",
}


def _semantic_tool_output(raw_tool_response):
    if isinstance(raw_tool_response, list):
        return [
            normalized
            for normalized in (_semantic_tool_output(value) for value in raw_tool_response)
            if normalized not in (None, {}, [])
        ]
    if not isinstance(raw_tool_response, dict):
        return raw_tool_response
    result = {}
    for key, value in raw_tool_response.items():
        if key in VOLATILE_TOOL_OUTPUT_KEYS or value is None:
            continue
        normalized = _semantic_tool_output(value)
        if normalized not in (None, {}, []):
            result[key] = normalized
    return result


def _content_hash(tool_name, normalized_input, normalized_output):
    stable = json.dumps(
        {
            "toolName": tool_name or "",
            "input": normalized_input or "",
            "output": normalized_output or "",
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return "sha256:" + hashlib.sha256(stable.encode("utf-8")).hexdigest()


def _tool_tokens(tool_name):
    if not tool_name:
        return []
    separated = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", str(tool_name))
    return [part for part in re.split(r"[^A-Za-z0-9]+", separated.lower()) if part]


def _has_token(tokens, values):
    return any(token in values for token in tokens)


def _first_string(mapping, keys):
    if not isinstance(mapping, dict):
        return None
    for key in keys:
        value = mapping.get(key)
        if isinstance(value, str) and value.strip():
            return value
    return None


def _path_values(tool_input):
    if not isinstance(tool_input, dict):
        return []
    values = []
    for key in PATH_KEYS:
        value = tool_input.get(key)
        if isinstance(value, str) and value.strip():
            values.append(value)
    for key in PATH_LIST_KEYS:
        value = tool_input.get(key)
        if isinstance(value, list):
            values.extend(item for item in value if isinstance(item, str) and item.strip())
    deduped = []
    seen = set()
    for value in values:
        normalized = value.strip()
        if normalized not in seen:
            seen.add(normalized)
            deduped.append(normalized)
    return deduped


def _validation_type(command):
    if not command:
        return None
    if any(pattern.search(command) for pattern in TEST_COMMAND_PATTERNS):
        return "test"
    if any(pattern.search(command) for pattern in LINT_COMMAND_PATTERNS):
        return "lint"
    if any(pattern.search(command) for pattern in TYPECHECK_COMMAND_PATTERNS):
        return "typecheck"
    if any(pattern.search(command) for pattern in BUILD_COMMAND_PATTERNS):
        return "build"
    return None


def _tool_operation(tokens):
    if "multi" in tokens and "edit" in tokens:
        return "multi_edit"
    for operation in ["read", "view", "open", "edit", "write", "patch", "replace", "update"]:
        if operation in tokens:
            return operation
    return None


def _tool_normalization(tool_name, tool_input):
    tokens = _tool_tokens(tool_name)
    metadata = {"normalizationVersion": NORMALIZATION_VERSION}
    command = _first_string(tool_input, COMMAND_KEYS)
    paths = _path_values(tool_input)

    if _has_token(tokens, {"bash", "shell", "exec", "run", "command"}) or command:
        validation_type = _validation_type(command)
        if validation_type:
            metadata["validationType"] = validation_type
        metadata["toolCategory"] = "command"
        return {
            "kind": "test_result" if validation_type == "test" else "command",
            "command": command,
            "operation": "run",
            "metadata": metadata,
        }

    if _has_token(tokens, {"read", "view", "open"}) and not _has_token(tokens, {"thread"}):
        metadata["toolCategory"] = "file"
        return {
            "kind": "file_read",
            "path": paths[0] if paths else None,
            "operation": "read",
            "metadata": _with_paths(metadata, paths),
        }

    if _has_token(tokens, {"edit", "write", "patch", "replace", "update"}):
        metadata["toolCategory"] = "file"
        return {
            "kind": "file_edit",
            "path": paths[0] if paths else None,
            "operation": _tool_operation(tokens) or "edit",
            "metadata": _with_paths(metadata, paths),
        }

    if _has_token(tokens, {"web", "fetch", "http"}):
        metadata["toolCategory"] = "web_search" if "search" in tokens else "web_fetch"
        url = _first_string(tool_input, URL_KEYS)
        query = _first_string(tool_input, ["query"])
        if url:
            metadata["url"] = url
        if query:
            metadata["query"] = query
        return {"kind": "tool_result", "operation": metadata["toolCategory"], "metadata": metadata}

    if _has_token(tokens, {"grep", "glob", "search", "find", "rg"}):
        metadata["toolCategory"] = "search"
        pattern = _first_string(tool_input, SEARCH_PATTERN_KEYS)
        if pattern:
            metadata["searchPattern"] = pattern
        return {
            "kind": "tool_result",
            "path": paths[0] if paths else None,
            "operation": "search",
            "metadata": _with_paths(metadata, paths),
        }

    if _has_token(tokens, {"ls", "list"}):
        metadata["toolCategory"] = "list"
        return {
            "kind": "tool_result",
            "path": paths[0] if paths else None,
            "operation": "list",
            "metadata": _with_paths(metadata, paths),
        }

    if _has_token(tokens, {"todo"}):
        metadata["toolCategory"] = "todo"
        return {"kind": "tool_result", "operation": "todo", "metadata": metadata}

    if _has_token(tokens, {"task", "agent", "subagent"}):
        metadata["toolCategory"] = "subagent"
        return {"kind": "tool_result", "operation": "subagent", "metadata": metadata}

    metadata["toolCategory"] = "unknown"
    return {
        "kind": "tool_result",
        "path": paths[0] if paths else None,
        "operation": "unknown",
        "metadata": _with_paths(metadata, paths),
    }


def _with_paths(metadata, paths):
    if len(paths) > 1:
        metadata = dict(metadata)
        metadata["paths"] = paths
    return metadata


def _redact_metadata(metadata):
    redacted = {}
    redaction_kinds = []
    for key, value in metadata.items():
        if isinstance(value, str):
            item, kinds = redact_text(value)
            redacted[key] = item
            redaction_kinds.extend(kinds)
        elif isinstance(value, list):
            items = []
            for item in value:
                if isinstance(item, str):
                    redacted_item, kinds = redact_text(item)
                    items.append(redacted_item)
                    redaction_kinds.extend(kinds)
                else:
                    items.append(item)
            redacted[key] = items
        else:
            redacted[key] = value
    return redacted, sorted(set(redaction_kinds))


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
    source_client = hook_input.get("source_client") or "claude-code"
    session_id = hook_input.get("session_id") or "unknown-session"
    metadata = {
        "hookEventName": hook_input.get("hook_event_name"),
        "sessionId": session_id,
        "sourceClient": source_client,
    }
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


def normalize_notification_event(hook_input, seq, turn_id=None, turn_seq=None):
    text, redaction_kinds = redact_text(
        hook_input.get("message")
        or hook_input.get("notification")
        or hook_input.get("text")
        or ""
    )
    event = _base_event(hook_input, seq, "notification", turn_id, turn_seq, text)
    event["text"] = text
    event["status"] = "success"
    metadata = dict(event["metadata"])
    if _looks_blocking_notification(text):
        metadata["notificationKind"] = "blocked"
        metadata["failureSignal"] = text
    else:
        metadata["notificationKind"] = "info"
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = metadata
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_subagent_stop_event(hook_input, seq, turn_id=None, turn_seq=None):
    subagent_type = (
        hook_input.get("subagent_type")
        or hook_input.get("subagentType")
        or hook_input.get("type")
    )
    text, redaction_kinds = redact_text(
        hook_input.get("message")
        or hook_input.get("summary")
        or hook_input.get("result")
        or ""
    )
    event = _base_event(hook_input, seq, "subagent_stop", turn_id, turn_seq, text)
    event["text"] = text
    event["operation"] = subagent_type
    event["status"] = "success"
    metadata = dict(event["metadata"])
    if subagent_type:
        metadata["subagentType"] = subagent_type
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = metadata
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_compact_boundary_event(hook_input, seq, turn_id=None, turn_seq=None):
    event = _base_event(hook_input, seq, "compact_boundary", turn_id, turn_seq, "compact")
    event["status"] = "success"
    event["operation"] = hook_input.get("trigger") or hook_input.get("compact_reason") or "compact"
    return {key: value for key, value in event.items() if value is not None and value != ""}


def normalize_session_end_event(hook_input, seq, turn_id=None, turn_seq=None):
    event = _base_event(hook_input, seq, "session_end", turn_id, turn_seq, "session_end")
    event["status"] = "success"
    event["operation"] = hook_input.get("reason") or hook_input.get("session_end_reason") or "session_end"
    return {key: value for key, value in event.items() if value is not None and value != ""}


def _looks_blocking_notification(text):
    lowered = (text or "").lower()
    return any(token in lowered for token in ["permission", "blocked", "denied", "failed", "error"])


def normalize_hook_event(hook_input, seq, turn_id=None, turn_seq=None):
    source_client = hook_input.get("source_client") or "claude-code"
    session_id = hook_input.get("session_id") or "unknown-session"
    tool_name = hook_input.get("tool_name")
    raw_tool_input = hook_input.get("tool_input")
    raw_tool_response = hook_input.get("tool_response")
    tool_input = raw_tool_input if isinstance(raw_tool_input, dict) else {}
    tool_response = raw_tool_response if isinstance(raw_tool_response, dict) else {}
    exit_code = tool_response.get("exit_code") if isinstance(tool_response, dict) else None
    redaction_kinds = []
    normalization = _tool_normalization(tool_name, tool_input)
    event_kind = normalization["kind"]

    event = {
        "eventId": event_id(source_client, session_id, seq, hook_input, event_kind),
        "seq": seq,
        "kind": event_kind,
        "occurredAt": hook_input.get("timestamp"),
        "toolName": tool_name,
    }
    if normalization.get("path"):
        path, kinds = redact_text(normalization["path"])
        event["path"] = path
        redaction_kinds.extend(kinds)
    if normalization.get("operation"):
        event["operation"] = normalization["operation"]
    if normalization.get("command") is not None:
        command, kinds = redact_text(normalization.get("command") or "")
        event["command"] = command
        redaction_kinds.extend(kinds)
    else:
        redacted_input, kinds = _redact_value(raw_tool_input if raw_tool_input is not None else {})
        event["input"] = _json_text(redacted_input)
        redaction_kinds.extend(kinds)

    if exit_code is not None:
        event["exitCode"] = exit_code
        event["status"] = "success" if exit_code == 0 else "failed"
    else:
        event["status"] = "success" if hook_input.get("hook_event_name") == "PostToolUse" else "running"

    output = _semantic_tool_output(raw_tool_response)
    if output:
        redacted_output, kinds = _redact_value(output)
        event["output"] = _json_text(redacted_output)
        redaction_kinds.extend(kinds)

    telemetry = _tool_telemetry(hook_input, tool_response)
    if telemetry.get("durationMs") is not None:
        event["durationMs"] = telemetry["durationMs"]
    if telemetry.get("inputTokens") is not None:
        event["inputTokens"] = telemetry["inputTokens"]
    if telemetry.get("outputTokens") is not None:
        event["outputTokens"] = telemetry["outputTokens"]
    event["contentHash"] = _content_hash(
        tool_name,
        event.get("command") if event.get("command") is not None else event.get("input"),
        event.get("output"),
    )

    metadata = {
        "hookEventName": hook_input.get("hook_event_name"),
        "sessionId": session_id,
        "sourceClient": source_client,
    }
    normalization_metadata, kinds = _redact_metadata(normalization.get("metadata") or {})
    metadata.update(normalization_metadata)
    redaction_kinds.extend(kinds)
    if turn_id:
        metadata["turnId"] = turn_id
    if turn_seq is not None:
        metadata["turnSeq"] = turn_seq
    if redaction_kinds:
        metadata["redacted"] = True
        metadata["redactionKinds"] = sorted(set(redaction_kinds))
    event["metadata"] = {key: value for key, value in metadata.items() if value is not None}
    return {key: value for key, value in event.items() if value is not None}


def append_event(state, event):
    state.append_agent_event(event)


def build_timeline_payload(config, identity, session_id, events, hook_input):
    source_client = config.get("sourceClient") or "claude-code"
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
            "sessionId": session_id,
            "sourceClient": source_client,
            "eventIds": [event["eventId"] for event in events if event.get("eventId")],
        },
    }
    if turn_id:
        payload["metadata"]["turnId"] = turn_id
    if turn_seq is not None:
        payload["metadata"]["turnSeq"] = turn_seq
    if cwd:
        path = Path(cwd)
        slug = project_slug(path)
        payload["project"] = {
            "name": path.name,
            "rootPath": str(path),
            "metadata": {"projectSlug": slug},
        }
        payload["metadata"]["projectSlug"] = slug
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
