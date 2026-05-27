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

import html

DEFAULT_RECENT_SESSIONS = 3
DEFAULT_MAX_ITEMS = 6
DEFAULT_MAX_CHARS = 6000

SECTION_ORDER = [
    ("recentRawData", "## Continue From"),
    ("directive", "## Must Follow"),
    ("watchOut", "## Watch Outs"),
    ("playbook", "## Reusable Playbooks"),
    ("fact", "## Useful Facts"),
]


def project_metadata_filter(project_slug):
    return {"all": [{"path": "projectSlug", "op": "eq", "value": project_slug}]}


def build_session_context(client, identity, project_slug, config):
    metadata_filter = project_metadata_filter(project_slug)
    recent_limit = int(config.get("sessionContextRecentSessions", DEFAULT_RECENT_SESSIONS))
    max_items = int(config.get("sessionContextMaxItems", DEFAULT_MAX_ITEMS))

    raw_data = client.query_raw_data(
        identity["userId"],
        identity["agentId"],
        types=["agent_timeline"],
        metadata_filter=metadata_filter,
        include={"metadata": True, "segment": False},
        limit=recent_limit,
    )
    recent_raw_data = [_raw_data_entry(raw) for raw in getattr(raw_data, "raw_data", [])]
    recent_raw_data = [entry for entry in recent_raw_data if entry.get("caption")]

    directives = _query_items(
        client,
        identity,
        ["directive"],
        metadata_filter,
        max_items,
    )
    watch_outs = _query_items(
        client,
        identity,
        ["resolution"],
        metadata_filter,
        max_items,
    )
    playbooks = _query_items(
        client,
        identity,
        ["playbook"],
        metadata_filter,
        max_items,
    )
    facts = _query_items(
        client,
        identity,
        ["event", "profile", "behavior", "tool"],
        metadata_filter,
        max_items,
    )

    return {
        "projectSlug": project_slug,
        "recentRawData": recent_raw_data,
        "items": {
            "directive": directives,
            "watchOut": watch_outs,
            "playbook": playbooks,
            "fact": facts,
        },
    }


def render_session_context(context, config):
    project_slug = context.get("projectSlug") or "unknown"
    max_chars = int(config.get("sessionContextMaxChars", DEFAULT_MAX_CHARS))
    header = f'<memind_session_context project="{html.escape(project_slug, quote=True)}">'
    preamble = (
        "Memind project memory. Use only when directly helpful. Prefer explicit "
        "user instructions and repository files over memory if they conflict."
    )
    footer = "</memind_session_context>"
    lines = [header, preamble]

    for key, title in SECTION_ORDER:
        entries = _section_entries(context, key)
        if not entries:
            continue
        lines.append("")
        lines.append(title)
        for entry in entries:
            lines.append(_render_entry(key, entry))

    if len(lines) <= 2:
        return ""

    full = "\n".join(lines + [footer])
    if len(full) <= max_chars:
        return full

    return _truncate_lines(lines, footer, max_chars)


def _query_items(client, identity, categories, metadata_filter, limit):
    response = client.query_items(
        identity["userId"],
        identity["agentId"],
        categories=categories,
        raw_data_types=["agent_timeline"],
        metadata_filter=metadata_filter,
        limit=limit,
    )
    return [_item_entry(item) for item in getattr(response, "items", []) if _text(item)]


def _raw_data_entry(raw):
    return {
        "id": _field(raw, "id"),
        "caption": _field(raw, "caption"),
        "createdAt": _field(raw, "created_at"),
        "metadata": _field(raw, "metadata") or {},
    }


def _item_entry(item):
    return {
        "id": _field(item, "id"),
        "text": _text(item),
        "category": (_field(item, "category") or "").lower(),
        "createdAt": _field(item, "created_at"),
        "metadata": _field(item, "metadata") or {},
    }


def _section_entries(context, key):
    if key == "recentRawData":
        return context.get("recentRawData") or []
    return (context.get("items") or {}).get(key) or []


def _render_entry(section_key, entry):
    if section_key == "recentRawData":
        return f"- [rawdata:{entry.get('id')}] {_clean(entry.get('caption'))}"
    category = entry.get("category") or "memory"
    return f"- [item:{entry.get('id')} {category}] {_clean(entry.get('text'))}"


def _truncate_lines(lines, footer, max_chars):
    notice = "\n[truncated]\n"
    budget = max(0, max_chars - len(notice) - len(footer))
    selected = []
    used = 0
    for line in lines:
        addition = len(line) + (1 if selected else 0)
        if used + addition > budget:
            break
        selected.append(line)
        used += addition
    if len(selected) <= 2:
        base = "\n".join(lines[:2])
        available = max(0, budget - len(base))
        base = base[:available]
        return f"{base}{notice}{footer}"[:max_chars]
    return f"{chr(10).join(selected)}{notice}{footer}"[:max_chars]


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)


def _text(item):
    return _field(item, "text")


def _clean(value):
    return " ".join(str(value or "").split())
