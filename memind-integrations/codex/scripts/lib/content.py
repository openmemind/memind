import hashlib
import json
import re
from pathlib import Path

MEMIND_BLOCK_RE = re.compile(r"<memind_memories>.*?</memind_memories>", re.DOTALL)
CODEX_CONTROL_BLOCK_RE = re.compile(
    r"<(?P<tag>environment_context|permissions instructions|collaboration_mode|skills_instructions|plugins_instructions|personality_spec)>.*?</(?P=tag)>",
    re.DOTALL,
)
INTERRUPTION_RE = re.compile(r"^\[(?:Request|Response) interrupted by user\]$")
TEXT_BLOCK_TYPES = {"input_text", "output_text", "text"}
TOOL_TYPES = {
    "local_shell_call",
    "function_call",
    "function_call_output",
    "custom_tool_call",
    "custom_tool_call_output",
    "web_search_call",
    "event_msg",
}


def strip_memind_blocks(text):
    return MEMIND_BLOCK_RE.sub("", text or "")


def _strip_codex_control_blocks(text):
    return CODEX_CONTROL_BLOCK_RE.sub("", text or "")


def _is_noise_text(text):
    normalized = (text or "").strip()
    return not normalized or bool(INTERRUPTION_RE.match(normalized))


def _parse_jsonl(path):
    for index, line in enumerate(Path(path).read_text(errors="ignore").splitlines()):
        if not line.strip():
            continue
        try:
            yield index, json.loads(line)
        except json.JSONDecodeError:
            continue


def _entry_payload(entry):
    if entry.get("type") == "response_item":
        payload = entry.get("payload") or {}
        payload_type = str(payload.get("type", "")).lower()
        if payload_type in TOOL_TYPES or payload_type != "message":
            return None
        return payload
    if str(entry.get("type", "")).lower() in TOOL_TYPES:
        return None
    if entry.get("role"):
        return entry
    return None


def _text_blocks(content):
    if isinstance(content, str):
        text = _strip_codex_control_blocks(strip_memind_blocks(content)).strip()
        return [] if _is_noise_text(text) else [text]
    if isinstance(content, list):
        texts = []
        for block in content:
            if not isinstance(block, dict):
                continue
            block_type = str(block.get("type", "")).lower()
            if block_type in TEXT_BLOCK_TYPES and block.get("text"):
                text = _strip_codex_control_blocks(strip_memind_blocks(block.get("text", ""))).strip()
                if not _is_noise_text(text):
                    texts.append(text)
        merged = "\n\n".join(texts).strip()
        return [] if _is_noise_text(merged) else [merged]
    return []


def _stable_id(entry):
    payload = entry.get("payload") if isinstance(entry.get("payload"), dict) else {}
    return entry.get("uuid") or entry.get("id") or payload.get("id")


def _timestamp(entry):
    payload = entry.get("payload") if isinstance(entry.get("payload"), dict) else {}
    return entry.get("timestamp") or entry.get("created_at") or payload.get("timestamp") or payload.get("created_at")


def fingerprint_message(entry, role, text, line_index):
    stable_id = _stable_id(entry)
    if stable_id:
        source = ("id", stable_id, role)
    else:
        source = ("fallback", role, text, _timestamp(entry), line_index)
    serialized = json.dumps(source, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha1(serialized.encode("utf-8")).hexdigest()


def extract_messages(path, roles):
    allowed = {role.lower() for role in roles}
    messages = []
    for line_index, entry in _parse_jsonl(path):
        payload = _entry_payload(entry)
        if not payload:
            continue
        role_text = str(payload.get("role", "")).lower()
        if role_text not in allowed or role_text not in {"user", "assistant"}:
            continue
        if role_text == "assistant" and payload.get("phase") not in {None, "final_answer"}:
            continue
        texts = _text_blocks(payload.get("content"))
        if not texts:
            continue
        role = "USER" if role_text == "user" else "ASSISTANT"
        timestamp = payload.get("timestamp") or payload.get("created_at") or _timestamp(entry)
        user_name = payload.get("user_name") or payload.get("userName")
        for text in texts:
            messages.append(
                {
                    "fingerprint": fingerprint_message(entry, role, text, line_index),
                    "role": role,
                    "content": [{"type": "text", "text": text}],
                    "timestamp": timestamp,
                    "userName": user_name,
                }
            )
    return messages


def _tail_lines(path, max_bytes=65536):
    path = Path(path)
    with path.open("rb") as handle:
        handle.seek(0, 2)
        size = handle.tell()
        handle.seek(max(0, size - max_bytes))
        data = handle.read().decode("utf-8", errors="ignore")
    return data.splitlines()


def _entry_texts(entry):
    payload = _entry_payload(entry)
    if not payload:
        return None, []
    role_text = str(payload.get("role", "")).lower()
    if role_text not in {"user", "assistant"}:
        return None, []
    if role_text == "assistant" and payload.get("phase") not in {None, "final_answer"}:
        return None, []
    return role_text, _text_blocks(payload.get("content"))


def read_recent_context(path, turns):
    if not path or turns <= 0 or not Path(path).exists():
        return ""
    entries = []
    for line in reversed(_tail_lines(path)):
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue
        role, texts = _entry_texts(entry)
        if not role or not texts:
            continue
        entries.append((role, texts[0]))
        if len(entries) >= turns * 2:
            break
    entries.reverse()
    return "\n".join(f"{role}: {text}" for role, text in entries)
