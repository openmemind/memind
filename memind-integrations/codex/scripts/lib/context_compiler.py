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
import re
import string
from datetime import datetime

DEFAULT_MAX_CHARS = 6000
DEFAULT_SESSION_ENTRY_MAX_CHARS = 520
DEFAULT_RETRIEVAL_ENTRY_MAX_CHARS = 700

SESSION_SECTION_ORDER = [
    ("continueFrom", "## Continue From"),
    ("mustFollow", "## Must Follow"),
    ("watchOuts", "## Watch Outs"),
    ("playbooks", "## Reusable Playbooks"),
    ("facts", "## Useful Facts"),
]

SESSION_SECTION_BUDGETS = {
    "continueFrom": 1300,
    "mustFollow": 1300,
    "watchOuts": 1400,
    "playbooks": 1200,
    "facts": 800,
}

PROMPT_SECTION_ORDER = [
    ("directives", "## Directives"),
    ("resolvedProblems", "## Resolved Problems"),
    ("playbooks", "## Agent Playbooks"),
    ("toolNotes", "## Tool Notes"),
    ("insights", "## Insights"),
    ("memoryItems", "## Memory Items"),
]

PROMPT_SECTION_BUDGETS = {
    "directives": 900,
    "resolvedProblems": 1600,
    "playbooks": 1200,
    "toolNotes": 900,
    "insights": 800,
    "memoryItems": 600,
}

PROMPT_SECTION_LIMITS = {
    "directives": 3,
    "resolvedProblems": 5,
    "playbooks": 4,
    "toolNotes": 3,
    "insights": 3,
    "memoryItems": 3,
}

TOOL_SECTION_ORDER = [
    ("priorResolutions", "## Prior Resolutions"),
    ("validationNotes", "## Validation Notes"),
    ("relevantPlaybooks", "## Relevant Playbooks"),
    ("directives", "## Directives"),
    ("recentEvidence", "## Recent Evidence"),
]

TOOL_SECTION_BUDGETS = {
    "priorResolutions": 900,
    "validationNotes": 700,
    "relevantPlaybooks": 700,
    "directives": 500,
    "recentEvidence": 700,
}

ALL_SECTION_ORDER = SESSION_SECTION_ORDER + PROMPT_SECTION_ORDER + TOOL_SECTION_ORDER

SECTION_FIT_PRIORITY = {
    "memind_session_context": ["mustFollow", "watchOuts", "continueFrom", "playbooks", "facts"],
    "memind_memories": ["directives", "resolvedProblems", "playbooks", "toolNotes", "insights", "memoryItems"],
    "memind_tool_context": [
        "priorResolutions",
        "validationNotes",
        "directives",
        "relevantPlaybooks",
        "recentEvidence",
    ],
}

WATCH_OUT_TERMS = {
    "error",
    "failed",
    "failure",
    "fix",
    "fixed",
    "regression",
    "test",
    "timeout",
    "retry",
    "avoid",
}

PLAYBOOK_TERMS = {"run", "after", "before", "when", "then", "workflow", "steps", "verify"}


def compile_session_start_context(context, config):
    sections = {
        "continueFrom": _normalize_rawdata(context.get("recentRawData") or []),
        "mustFollow": _normalize_items(((context.get("items") or {}).get("directive") or []), "mustFollow"),
        "watchOuts": _normalize_items(((context.get("items") or {}).get("watchOut") or []), "watchOuts"),
        "playbooks": _normalize_items(((context.get("items") or {}).get("playbook") or []), "playbooks"),
        "facts": _normalize_items(((context.get("items") or {}).get("fact") or []), "facts"),
    }
    project_slug = context.get("projectSlug") or "unknown"
    max_chars = int(config.get("sessionContextMaxChars", DEFAULT_MAX_CHARS))
    entry_max_chars = int(config.get("sessionContextEntryMaxChars", DEFAULT_SESSION_ENTRY_MAX_CHARS))
    return _render_context(
        wrapper="memind_session_context",
        attrs={"project": project_slug},
        preamble=(
            "Historical Memind project memory. Use only when directly helpful. "
            "Current user instructions and repository files take precedence. "
            "Verify old implementation details against the working tree before relying on them."
        ),
        sections=_prepare_sections(sections, "session_start"),
        order=SESSION_SECTION_ORDER,
        budgets=SESSION_SECTION_BUDGETS,
        max_chars=max_chars,
        entry_max_chars=entry_max_chars,
    )


def compile_prompt_retrieval_context(data, config):
    max_entries = int(config.get("retrieveMaxEntries", 8))
    max_chars = int(config.get("retrieveMaxChars", DEFAULT_MAX_CHARS))
    entry_max_chars = int(config.get("retrieveEntryMaxChars", DEFAULT_RETRIEVAL_ENTRY_MAX_CHARS))

    items = [_normalize_retrieved_item(item) for item in data.get("items") or [] if _field(item, "text")]
    insights = [_normalize_insight(insight) for insight in data.get("insights") or [] if _field(insight, "text")]
    sorted_items = _sort_prompt_items(items)

    selected_insights = _select_prompt_insights(insights, _section_limit("insights", max_entries))
    sections = {
        "directives": _top_category(sorted_items, "directive", _section_limit("directives", max_entries)),
        "resolvedProblems": _top_category(sorted_items, "resolution", _section_limit("resolvedProblems", max_entries)),
        "playbooks": _top_category(sorted_items, "playbook", _section_limit("playbooks", max_entries)),
        "toolNotes": _top_category(sorted_items, "tool", _section_limit("toolNotes", max_entries)),
        "insights": selected_insights,
        "memoryItems": _top_general_items(sorted_items, _section_limit("memoryItems", max_entries)),
    }

    degraded_notice = ""
    if data.get("status") == "degraded":
        degraded_notice = "[Note: Memory retrieval encountered an error. Results may be incomplete.]"

    preamble = config.get("retrievePromptPreamble") or (
        "Relevant Memind memories for the current request. Use only when directly helpful."
    )
    rendered = _render_context(
        wrapper="memind_memories",
        attrs=_prompt_attrs(data),
        preamble=preamble,
        sections=_prepare_sections(sections, "prompt_retrieval"),
        order=PROMPT_SECTION_ORDER,
        budgets=PROMPT_SECTION_BUDGETS,
        max_chars=max_chars,
        entry_max_chars=entry_max_chars,
        trailing_notice=degraded_notice,
    )
    if rendered or not degraded_notice:
        return rendered
    return _render_context(
        wrapper="memind_memories",
        attrs=_prompt_attrs(data),
        preamble=preamble,
        sections={},
        order=PROMPT_SECTION_ORDER,
        budgets=PROMPT_SECTION_BUDGETS,
        max_chars=max_chars,
        entry_max_chars=entry_max_chars,
        trailing_notice=degraded_notice,
        allow_notice_only=True,
    )


def _prompt_attrs(data):
    attrs = {}
    if data.get("projectSlug"):
        attrs["project"] = data["projectSlug"]
    if data.get("mode"):
        attrs["mode"] = data["mode"]
    return attrs


def compile_tool_context(context, config):
    target = context.get("target") or {}
    items = [_normalize_tool_item(item) for item in context.get("items") or [] if _field(item, "text")]
    raw_data = [_normalize_tool_rawdata(raw) for raw in context.get("rawData") or []]
    sections = {
        "priorResolutions": _top_category(items, "resolution", 2),
        "validationNotes": _top_category(items, "tool", 3),
        "relevantPlaybooks": _top_category(items, "playbook", 2),
        "directives": _top_category(items, "directive", 2),
        "recentEvidence": raw_data[:2],
    }

    attrs = {"tool": target.get("toolName") or "unknown"}
    if target.get("path"):
        attrs["file"] = target["path"]
    if target.get("command"):
        attrs["command"] = target["command"]
    if target.get("projectSlug"):
        attrs["project"] = target["projectSlug"]

    return _render_context(
        wrapper="memind_tool_context",
        attrs=attrs,
        preamble=(
            "Use only if directly relevant to this exact tool call. "
            "Current user instructions and repository files take precedence. "
            "Verify old details against the working tree before relying on them."
        ),
        sections=_prepare_sections(sections, "tool_context"),
        order=TOOL_SECTION_ORDER,
        budgets=TOOL_SECTION_BUDGETS,
        max_chars=int(config.get("toolContextMaxChars", 3500)),
        entry_max_chars=int(config.get("toolContextEntryMaxChars", 520)),
    )


def _prepare_sections(sections, mode):
    prepared = {}
    high_value_seen = set()
    for key, entries in sections.items():
        ranked = list(entries) if mode == "prompt_retrieval" and key == "insights" else _rank_entries(entries, key, mode)
        deduped = []
        section_seen = set()
        for entry in ranked:
            dedupe_key = _dedupe_key(entry["text"])
            if not dedupe_key or dedupe_key in section_seen:
                continue
            if key in {"facts", "memoryItems"} and dedupe_key in high_value_seen:
                continue
            section_seen.add(dedupe_key)
            deduped.append(entry)
        if key not in {"continueFrom", "facts", "memoryItems"}:
            high_value_seen.update(_dedupe_key(entry["text"]) for entry in deduped if _dedupe_key(entry["text"]))
        if deduped:
            prepared[key] = deduped
    return prepared


def _normalize_rawdata(raw_data):
    entries = []
    for raw in raw_data:
        text = _field(raw, "caption")
        if not text:
            continue
        entries.append(
            {
                "kind": "rawdata",
                "id": _field(raw, "id"),
                "category": "agent_timeline",
                "text": _clean(text),
                "createdAt": _field(raw, "createdAt") or _field(raw, "created_at"),
                "score": 0,
            }
        )
    return entries


def _normalize_items(items, section):
    entries = []
    for item in items:
        text = _field(item, "text")
        if not text:
            continue
        category = str(_field(item, "category") or "memory").strip().lower()
        entries.append(
            {
                "kind": "item",
                "id": _field(item, "id"),
                "category": category,
                "text": _clean(text),
                "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
                "score": _section_score(section, text),
            }
        )
    return entries


def _normalize_retrieved_item(item):
    category = str(_field(item, "category") or "memory").strip().lower()
    return {
        "kind": "item",
        "id": _field(item, "id"),
        "category": category,
        "text": _clean(_field(item, "text")),
        "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
        "score": _number(_field(item, "finalScore"), _field(item, "vectorScore"), 0),
        "source": _field(item, "memindContextSource"),
    }


def _normalize_insight(insight):
    return {
        "kind": "insight",
        "id": _field(insight, "id"),
        "category": str(_field(insight, "tier") or "insight").strip().lower(),
        "text": _clean(_field(insight, "text")),
        "createdAt": _field(insight, "createdAt") or _field(insight, "created_at"),
        "score": 0,
        "source": _field(insight, "memindContextSource"),
    }


def _normalize_tool_item(item):
    category = str(_field(item, "category") or "memory").strip().lower()
    return {
        "kind": "item",
        "id": _field(item, "id"),
        "category": category,
        "text": _clean(_field(item, "text")),
        "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
        "score": _number(_field(item, "score"), _field(item, "finalScore"), 0),
    }


def _normalize_tool_rawdata(raw):
    text = _field(raw, "caption") or _recent_evidence_from_metadata(_field(raw, "metadata") or {})
    return {
        "kind": "rawdata",
        "id": _field(raw, "id") or _field(raw, "rawDataId") or _field(raw, "raw_data_id"),
        "category": "agent_timeline",
        "text": _clean(text),
        "createdAt": _field(raw, "createdAt") or _field(raw, "created_at"),
        "score": 0,
    }


def _recent_evidence_from_metadata(metadata):
    stats = metadata.get("toolStats") or {}
    parts = []
    for tool_name, stat in stats.items():
        success = int(stat.get("successCount") or 0)
        failed = int(stat.get("failCount") or 0)
        if success or failed:
            parts.append(f"{tool_name} failed {failed} time(s) and passed {success} time(s)")
    return "; ".join(parts)


def _rank_entries(entries, section, mode):
    if section == "continueFrom":
        return sorted(entries, key=lambda entry: _timestamp(entry.get("createdAt")), reverse=True)[:3]
    return sorted(
        entries,
        key=lambda entry: (
            entry.get("score", 0),
            _timestamp(entry.get("createdAt")),
            -len(entry.get("text", "")),
        ),
        reverse=True,
    )


def _sort_prompt_items(items):
    return sorted(
        items,
        key=lambda entry: (
            entry.get("score", 0),
            _timestamp(entry.get("createdAt")),
            _category_priority(entry.get("category")),
        ),
        reverse=True,
    )


def _sort_insights(insights):
    tier_rank = {"root": 3, "branch": 2, "leaf": 1}
    return sorted(
        insights,
        key=lambda entry: (
            tier_rank.get(entry.get("category", ""), 0),
            _timestamp(entry.get("createdAt")),
            str(entry.get("id") or ""),
        ),
        reverse=True,
    )


def _select_prompt_insights(insights, limit):
    sorted_insights = _sort_insights(insights)
    high_level = [entry for entry in sorted_insights if entry.get("category") in {"root", "branch"}]
    return (high_level or sorted_insights)[:limit]


def _top_category(items, category, limit):
    return [entry for entry in items if entry["category"] == category][:limit]


def _top_general_items(items, limit):
    agent_categories = {"directive", "resolution", "playbook", "tool"}
    return [entry for entry in items if entry["category"] not in agent_categories][:limit]


def _section_limit(section, max_entries):
    return max(0, min(PROMPT_SECTION_LIMITS.get(section, max_entries), max_entries))


def _category_priority(category):
    return {"directive": 5, "resolution": 4, "playbook": 3, "tool": 2}.get(category or "", 1)


def _section_score(section, text):
    lowered = str(text or "").lower()
    if section == "watchOuts":
        return sum(1 for term in WATCH_OUT_TERMS if term in lowered)
    if section == "playbooks":
        return sum(1 for term in PLAYBOOK_TERMS if term in lowered)
    if section == "mustFollow":
        return 2 if len(lowered) <= 220 else 1
    return 0


def _render_context(
    wrapper,
    attrs,
    preamble,
    sections,
    order,
    budgets,
    max_chars,
    entry_max_chars,
    trailing_notice="",
    allow_notice_only=False,
):
    if not sections and not trailing_notice and not allow_notice_only:
        return ""

    open_tag = _open_tag(wrapper, attrs)
    close_tag = f"</{wrapper}>"
    fixed_lines = [open_tag, preamble]
    rendered_sections = []
    truncated = False

    for key, title in order:
        entries = sections.get(key) or []
        if not entries:
            continue
        budget = budgets.get(key, 800)
        lines, section_truncated = _render_section(title, entries, budget, entry_max_chars)
        truncated = truncated or section_truncated
        if lines:
            rendered_sections.append((key, lines))

    if trailing_notice:
        rendered_sections.append(("notice", [trailing_notice]))

    if not rendered_sections and not allow_notice_only:
        return ""

    lines = list(fixed_lines)
    for _key, section_lines in rendered_sections:
        lines.append("")
        lines.extend(section_lines)
    if truncated:
        lines.append("")
        lines.append("[truncated: lower-priority memories omitted]")
    lines.append(close_tag)

    rendered = "\n".join(lines)
    if len(rendered) <= max_chars:
        return rendered
    return _fit_sections_to_total_budget(
        fixed_lines,
        rendered_sections,
        close_tag,
        max_chars,
        SECTION_FIT_PRIORITY.get(wrapper, []),
    )


def _render_section(title, entries, budget, entry_max_chars):
    lines = [title]
    used = len(title)
    truncated = False
    for entry in entries:
        line = _render_entry(entry, entry_max_chars)
        addition = len(line) + 1
        if used + addition > budget:
            truncated = True
            break
        lines.append(line)
        used += addition
    return (lines if len(lines) > 1 else []), truncated


def _render_entry(entry, max_chars):
    date = _date_label(entry.get("createdAt"))
    if entry["kind"] == "rawdata":
        label = f"rawdata:{entry.get('id')}"
    elif entry["kind"] == "insight":
        label = f"insight:{entry.get('id')} {entry.get('category') or 'insight'}"
    else:
        label = f"item:{entry.get('id')} {entry.get('category') or 'memory'}"
    source = entry.get("source")
    label_parts = [label]
    if source:
        label_parts.append(source)
    if date:
        label_parts.append(date)
    label = ", ".join(label_parts)
    return f"- [{label}] {_clip(entry.get('text'), max_chars)}"


def _fit_sections_to_total_budget(fixed_lines, rendered_sections, close_tag, max_chars, priority):
    notice = "[truncated: lower-priority memories omitted]"
    full_suffix = f"\n{notice}\n{close_tag}"
    suffix = full_suffix if len(full_suffix) < max_chars else f"\n{close_tag}"
    selected_sections = []
    selected_keys = set()
    section_map = {key: lines for key, lines in rendered_sections}
    base = "\n".join(fixed_lines)
    used = len(base)
    budget = max(0, max_chars - len(suffix))

    for key in priority + [key for key, _lines in rendered_sections if key not in priority]:
        section_lines = section_map.get(key)
        if not section_lines or key in selected_keys:
            continue
        addition = len("\n\n" + "\n".join(section_lines))
        if used + addition > budget:
            break
        selected_sections.append((key, section_lines))
        selected_keys.add(key)
        used += addition

    order_index = {key: index for index, (key, _title) in enumerate(ALL_SECTION_ORDER)}
    selected_sections.sort(key=lambda item: order_index.get(item[0], 999))
    selected = list(fixed_lines)
    for _key, section_lines in selected_sections:
        selected.append("")
        selected.extend(section_lines)
    prefix = "\n".join(selected)
    if len(prefix) > budget:
        prefix = prefix[:budget].rstrip()
    result = f"{prefix}{suffix}" if prefix else suffix.lstrip()
    if len(result) <= max_chars:
        return result
    overflow = len(result) - max_chars
    prefix = prefix[:-overflow].rstrip() if overflow < len(prefix) else ""
    return f"{prefix}{suffix}" if prefix else suffix.lstrip()


def _open_tag(wrapper, attrs):
    if not attrs:
        return f"<{wrapper}>"
    rendered = " ".join(
        f'{name}="{html.escape(str(value), quote=True)}"' for name, value in attrs.items()
    )
    return f"<{wrapper} {rendered}>"


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)


def _clean(value):
    return " ".join(str(value or "").split())


def _clip(value, max_chars):
    cleaned = _clean(value)
    if len(cleaned) <= max_chars:
        return cleaned
    return cleaned[: max(0, max_chars - 12)].rstrip() + " [truncated]"


def _dedupe_key(value):
    cleaned = _clean(value).lower()
    cleaned = cleaned.translate(str.maketrans("", "", string.punctuation))
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned[:260]


def _timestamp(value):
    if not value:
        return 0
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0


def _date_label(value):
    if not value:
        return ""
    text = str(value)
    return text[:10] if len(text) >= 10 else text


def _number(*values):
    for value in values:
        if value is None:
            continue
        try:
            return float(value)
        except (TypeError, ValueError):
            continue
    return 0
