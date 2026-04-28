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

MEMIND_BLOCK_RE = re.compile(r"<memind_memories>.*?</memind_memories>", re.DOTALL)
CLAUDE_CODE_PLACEHOLDER_RE = re.compile(
    r"^\[(?:Request|Response) interrupted by user\]$"
)


def strip_memind_blocks(text):
    return MEMIND_BLOCK_RE.sub("", text or "")


def _is_noise_text(text):
    normalized = (text or "").strip()
    return not normalized or bool(CLAUDE_CODE_PLACEHOLDER_RE.match(normalized))


def _parse_jsonl(path):
    for line in Path(path).read_text(errors="ignore").splitlines():
        if not line.strip():
            continue
        try:
            yield json.loads(line)
        except json.JSONDecodeError:
            continue


def _text_blocks(content):
    if isinstance(content, str):
        text = strip_memind_blocks(content).strip()
        return [] if _is_noise_text(text) else [text]
    if isinstance(content, list):
        texts = []
        for block in content:
            if isinstance(block, dict) and block.get("type") == "text" and block.get("text"):
                text = strip_memind_blocks(block.get("text", "")).strip()
                if not _is_noise_text(text):
                    texts.append(text)
        merged = "\n\n".join(texts).strip()
        return [] if _is_noise_text(merged) else [merged]
    return []


def _fingerprint(entry, role, text):
    source = json.dumps(
        {
            "uuid": entry.get("uuid"),
            "timestamp": entry.get("timestamp"),
            "type": entry.get("type"),
            "role": role,
            "text": text,
        },
        sort_keys=True,
        ensure_ascii=False,
    )
    return hashlib.sha1(source.encode("utf-8")).hexdigest()


def extract_messages(path, roles):
    allowed = {role.lower() for role in roles}
    messages = []
    for entry in _parse_jsonl(path):
        entry_type = str(entry.get("type", "")).lower()
        if entry_type not in allowed or entry_type not in {"user", "assistant"}:
            continue
        content = (entry.get("message") or {}).get("content")
        texts = _text_blocks(content)
        if not texts:
            continue
        role = "USER" if entry_type == "user" else "ASSISTANT"
        for text in texts:
            messages.append(
                {
                    "fingerprint": _fingerprint(entry, role, text),
                    "role": role,
                    "content": [{"type": "text", "text": text}],
                    "timestamp": entry.get("timestamp"),
                    "userName": entry.get("user_name"),
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


def read_recent_context(path, turns):
    if not path or turns <= 0 or not Path(path).exists():
        return ""
    entries = []
    for line in reversed(_tail_lines(path)):
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue
        entry_type = str(entry.get("type", "")).lower()
        if entry_type not in {"user", "assistant"}:
            continue
        for text in _text_blocks((entry.get("message") or {}).get("content")):
            entries.append((entry_type, text))
            break
        if len(entries) >= turns * 2:
            break
    entries.reverse()
    return "\n".join(f"{role}: {text}" for role, text in entries)
