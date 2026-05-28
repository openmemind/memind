# Agent Context Compiler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic Agent Context Compiler for Claude Code and Codex so Memind memory is injected as high-value coding-agent context instead of lightly formatted query output.

**Architecture:** Keep Memind core, OpenAPI, and `rawdata-agent` unchanged. Implement the compiler in the Claude Code and Codex integration layers: normalize retrieved rawdata/items/insights into common entries, classify them into agent-oriented sections, rank and deduplicate them, then render bounded context for `SessionStart` and `UserPromptSubmit`. `rawdata-agent` remains responsible for ingestion and extraction; integrations remain responsible for prompt injection.

**Tech Stack:** Python 3.10+ hook scripts and `unittest`, Memind Python client query/retrieve APIs, Claude Code hooks, Codex hooks, existing integration settings and installers.

---

## Design Summary

The compiler has two modes:

- `session_start`: project-continuity context injected on `SessionStart`.
- `prompt_retrieval`: query-aware context injected on `UserPromptSubmit`.

Both modes use the same deterministic utilities:

- Normalize Memind rawdata/items/insights into `ContextEntry` dictionaries.
- Classify entries into stable sections.
- Rank entries by section-specific usefulness.
- Deduplicate repeated memory text within each section. Preserve `agent_timeline` captions in `Continue From` because they are turn summaries; do not let caption text remove structured `directive`, `resolution`, or `playbook` items. Allow lower-priority `facts` and generic memory items to be removed when they duplicate higher-value structured items.
- Render with section budgets, per-entry caps, stable source citations, and an explicit historical-memory preamble.

This plan intentionally avoids core/OpenAPI changes. It uses existing calls:

- `query_raw_data(types=["agent_timeline"], metadata_filter=project_filter, include={"metadata": True, "segment": False})`
- `query_items(categories=[...], raw_data_types=["agent_timeline"], metadata_filter=project_filter)`
- `memory.retrieve(query=...)`

## File Structure

Create:

- `memind-integrations/claude-code/scripts/lib/context_compiler.py`
  - Owns normalization, classification, ranking, dedupe, budgets, and rendering for Claude Code.
- `memind-integrations/codex/scripts/lib/context_compiler.py`
  - Same implementation for Codex so the installed adapter is self-contained.
- `memind-integrations/claude-code/tests/test_context_compiler.py`
  - Unit tests for compiler behavior independent of hook subprocess tests.
- `memind-integrations/codex/tests/test_context_compiler.py`
  - Same coverage for Codex.

Modify:

- `memind-integrations/claude-code/scripts/lib/session_context.py`
  - Keep fetching project memory, delegate rendering to `context_compiler`.
- `memind-integrations/codex/scripts/lib/session_context.py`
  - Same as Claude Code.
- `memind-integrations/claude-code/scripts/retrieve.py`
  - Replace local `_format_context` with `compile_prompt_retrieval_context`.
- `memind-integrations/codex/scripts/retrieve.py`
  - Same as Claude Code.
- `memind-integrations/claude-code/tests/test_session_context.py`
  - Assert fetch behavior remains unchanged and new rendering behavior appears.
- `memind-integrations/codex/tests/test_session_context.py`
  - Same as Claude Code.
- `memind-integrations/claude-code/tests/test_hooks.py`
  - Update `_format_context` tests to target compiler through `retrieve.py` or move assertions to `test_context_compiler.py`.
- `memind-integrations/codex/tests/test_hooks.py`
  - Same as Claude Code.
- `memind-integrations/codex/install.sh`
  - Add `scripts/lib/context_compiler.py` to remote install file list.
- `memind-integrations/codex/tests/test_installer.py`
  - Assert `context_compiler.py` is included in installer file checks.
- `memind-integrations/claude-code/README.md`
  - Update SessionStart and retrieval examples.
- `memind-integrations/codex/README.md`
  - Same as Claude Code.

Do not modify:

- `memind-core`
- `memind-server`
- `memind-plugins/memind-plugin-rawdatas/memind-plugin-rawdata-agent`
- OpenAPI schema or generated models

---

## Task 1: Add Shared Compiler Behavior to Claude Code

**Files:**

- Create: `memind-integrations/claude-code/scripts/lib/context_compiler.py`
- Create: `memind-integrations/claude-code/tests/test_context_compiler.py`

- [ ] **Step 1: Write failing tests for session-start compilation**

Create `memind-integrations/claude-code/tests/test_context_compiler.py` with this starting content:

```python
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from scripts.lib.context_compiler import compile_session_start_context


class ContextCompilerTest(unittest.TestCase):
    def test_session_start_context_ranks_dedupes_and_preserves_priority_sections(self):
        context = {
            "projectSlug": "memind-main",
            "recentRawData": [
                {
                    "id": "rd-old",
                    "caption": "Older work on unrelated install docs.",
                    "createdAt": "2026-05-25T10:00:00Z",
                    "metadata": {},
                },
                {
                    "id": "rd-new",
                    "caption": "Completed SessionStart context injection for Claude Code and Codex; keep userId and agentId stable.",
                    "createdAt": "2026-05-27T10:00:00Z",
                    "metadata": {},
                },
            ],
            "items": {
                "directive": [
                    {
                        "id": "dir-1",
                        "category": "directive",
                        "text": "Keep userId and agentId stable; use metadata.projectSlug for project isolation.",
                        "createdAt": "2026-05-27T09:00:00Z",
                        "metadata": {},
                    }
                ],
                "watchOut": [
                    {
                        "id": "res-1",
                        "category": "resolution",
                        "text": "Codex tests must run with Python 3.12; older Python can fail on modern type syntax.",
                        "createdAt": "2026-05-27T08:00:00Z",
                        "metadata": {},
                    },
                    {
                        "id": "res-dup",
                        "category": "resolution",
                        "text": "codex tests must run with python 3.12 older python can fail on modern type syntax",
                        "createdAt": "2026-05-26T08:00:00Z",
                        "metadata": {},
                    },
                ],
                "playbook": [
                    {
                        "id": "pb-1",
                        "category": "playbook",
                        "text": "After changing Claude Code or Codex hooks, run both integration unittest suites and git diff --check.",
                        "createdAt": "2026-05-27T07:00:00Z",
                        "metadata": {},
                    }
                ],
                "fact": [
                    {
                        "id": "fact-1",
                        "category": "event",
                        "text": "SessionStart is read-only: it queries memory and injects context without writing rawdata.",
                        "createdAt": "2026-05-27T06:00:00Z",
                        "metadata": {},
                    }
                ],
            },
        }

        rendered = compile_session_start_context(context, {"sessionContextMaxChars": 6000})

        self.assertIn('<memind_session_context project="memind-main">', rendered)
        self.assertIn("Historical Memind project memory", rendered)
        self.assertIn("Current user instructions and repository files take precedence", rendered)
        self.assertIn("Verify old implementation details against the working tree", rendered)
        self.assertIn("## Continue From", rendered)
        self.assertIn("[rawdata:rd-new, 2026-05-27] Completed SessionStart context injection", rendered)
        self.assertLess(rendered.index("rd-new"), rendered.index("rd-old"))
        self.assertIn("## Must Follow", rendered)
        self.assertIn("[item:dir-1 directive, 2026-05-27] Keep userId and agentId stable", rendered)
        self.assertIn("rd-new", rendered, "turn-summary caption should stay in Continue From")
        self.assertIn("dir-1", rendered, "structured item should not be removed by caption text")
        self.assertIn("## Watch Outs", rendered)
        self.assertIn("[item:res-1 resolution, 2026-05-27] Codex tests must run", rendered)
        self.assertNotIn("res-dup", rendered)
        self.assertIn("## Reusable Playbooks", rendered)
        self.assertIn("[item:pb-1 playbook, 2026-05-27] After changing Claude Code", rendered)
        self.assertIn("## Useful Facts", rendered)
        self.assertIn("[item:fact-1 event, 2026-05-27] SessionStart is read-only", rendered)
        self.assertTrue(rendered.endswith("</memind_session_context>"))

    def test_session_start_context_uses_section_budget_instead_of_naive_line_truncation(self):
        context = {
            "projectSlug": "memind-main",
            "recentRawData": [
                {"id": "rd-1", "caption": "Recent work " + "A" * 500, "createdAt": "2026-05-27T01:00:00Z"},
            ],
            "items": {
                "directive": [
                    {"id": "dir-1", "category": "directive", "text": "Do not break stable identity.", "createdAt": "2026-05-27T01:00:00Z"}
                ],
                "watchOut": [
                    {"id": "res-1", "category": "resolution", "text": "Always avoid committing __pycache__ files.", "createdAt": "2026-05-27T01:00:00Z"}
                ],
                "playbook": [
                    {"id": "pb-1", "category": "playbook", "text": "Run focused integration tests after hook changes.", "createdAt": "2026-05-27T01:00:00Z"}
                ],
                "fact": [
                    {"id": "fact-1", "category": "event", "text": "Fact " + "F" * 400, "createdAt": "2026-05-27T01:00:00Z"}
                ],
            },
        }

        rendered = compile_session_start_context(context, {"sessionContextMaxChars": 900})

        self.assertLessEqual(len(rendered), 900)
        self.assertIn("## Must Follow", rendered)
        self.assertIn("Do not break stable identity.", rendered)
        self.assertIn("## Watch Outs", rendered)
        self.assertIn("Always avoid committing __pycache__", rendered)
        self.assertIn("truncated: lower-priority memories omitted", rendered)
        self.assertTrue(rendered.endswith("</memind_session_context>"))

    def test_session_start_context_returns_empty_when_no_entries_exist(self):
        rendered = compile_session_start_context(
            {"projectSlug": "memind-main", "recentRawData": [], "items": {}},
            {"sessionContextMaxChars": 6000},
        )

        self.assertEqual(rendered, "")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the failing compiler tests**

Run:

```bash
python3 -m unittest tests/test_context_compiler.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'scripts.lib.context_compiler'`.

- [ ] **Step 3: Implement Claude Code context compiler**

Create `memind-integrations/claude-code/scripts/lib/context_compiler.py`:

```python
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

    sections = {
        "directives": _top_category(sorted_items, "directive", _section_limit("directives", max_entries)),
        "resolvedProblems": _top_category(sorted_items, "resolution", _section_limit("resolvedProblems", max_entries)),
        "playbooks": _top_category(sorted_items, "playbook", _section_limit("playbooks", max_entries)),
        "toolNotes": _top_category(sorted_items, "tool", _section_limit("toolNotes", max_entries)),
        "insights": _sort_insights(insights)[: _section_limit("insights", max_entries)],
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
        attrs={},
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
        attrs={},
        preamble=preamble,
        sections={},
        order=PROMPT_SECTION_ORDER,
        budgets=PROMPT_SECTION_BUDGETS,
        max_chars=max_chars,
        entry_max_chars=entry_max_chars,
        trailing_notice=degraded_notice,
        allow_notice_only=True,
    )


def _prepare_sections(sections, mode):
    prepared = {}
    high_value_seen = set()
    for key, entries in sections.items():
        ranked = _rank_entries(entries, key, mode)
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
    }


def _normalize_insight(insight):
    return {
        "kind": "insight",
        "id": _field(insight, "id"),
        "category": str(_field(insight, "tier") or "insight").strip().lower(),
        "text": _clean(_field(insight, "text")),
        "createdAt": _field(insight, "createdAt") or _field(insight, "created_at"),
        "score": 0,
    }


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
            rendered_sections.append(lines)

    if trailing_notice:
        rendered_sections.append([trailing_notice])

    if not rendered_sections and not allow_notice_only:
        return ""

    lines = list(fixed_lines)
    for section_lines in rendered_sections:
        lines.append("")
        lines.extend(section_lines)
    if truncated:
        lines.append("")
        lines.append("[truncated: lower-priority memories omitted]")
    lines.append(close_tag)

    rendered = "\n".join(lines)
    if len(rendered) <= max_chars:
        return rendered
    return _fit_to_total_budget(lines, close_tag, max_chars)


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
    if date:
        label = f"{label}, {date}"
    return f"- [{label}] {_clip(entry.get('text'), max_chars)}"


def _fit_to_total_budget(lines, close_tag, max_chars):
    notice = "[truncated: lower-priority memories omitted]"
    full_suffix = f"\n{notice}\n{close_tag}"
    suffix = full_suffix if len(full_suffix) < max_chars else f"\n{close_tag}"
    selected = []
    budget = max(0, max_chars - len(suffix))
    used = 0
    for line in lines:
        if line == close_tag or line == notice:
            continue
        addition = len(line) + (1 if selected else 0)
        if used + addition > budget:
            break
        selected.append(line)
        used += addition
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
```

- [ ] **Step 4: Run Claude Code compiler tests**

Run:

```bash
python3 -m unittest tests/test_context_compiler.py -v
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
git add memind-integrations/claude-code/scripts/lib/context_compiler.py memind-integrations/claude-code/tests/test_context_compiler.py
git commit -m "feat: add claude code agent context compiler"
```

Expected: commit succeeds.

---

## Task 2: Wire Claude Code SessionStart to the Compiler

**Files:**

- Modify: `memind-integrations/claude-code/scripts/lib/session_context.py`
- Modify: `memind-integrations/claude-code/tests/test_session_context.py`

- [ ] **Step 1: Update failing SessionStart expectations**

Modify `memind-integrations/claude-code/tests/test_session_context.py`:

```python
from scripts.lib.session_context import build_session_context, render_session_context
```

Keep the import as-is, then update `test_build_session_context_queries_current_project_memory` assertions to include the improved preamble and citations:

```python
self.assertIn("Historical Memind project memory", rendered)
self.assertIn("Current user instructions and repository files take precedence", rendered)
self.assertIn("Verify old implementation details against the working tree", rendered)
self.assertIn("[rawdata:rd-1, 2026-05-27] Implemented generic memory query APIs", rendered)
self.assertIn("[item:it-1 directive] Keep userId and agentId stable.", rendered)
```

Keep the existing API call assertions unchanged.

- [ ] **Step 2: Run SessionStart tests to verify failure**

Run:

```bash
python3 -m unittest tests/test_session_context.py -v
```

Expected: FAIL because `render_session_context` still emits the old preamble and rawdata citations without dates.

- [ ] **Step 3: Delegate rendering from session_context to context_compiler**

Modify `memind-integrations/claude-code/scripts/lib/session_context.py`.

Add import near the top:

```python
from lib.context_compiler import compile_session_start_context
```

Replace the body of `render_session_context` with:

```python
def render_session_context(context, config):
    return compile_session_start_context(context, config)
```

Delete these old rendering-only symbols from `session_context.py` after `render_session_context` delegates to the compiler:

- `html`
- `SECTION_ORDER`
- `_section_entries`
- `_render_entry`
- `_truncate_lines`
- `_clean`

Keep fetch helpers:

- `DEFAULT_RECENT_SESSIONS`
- `DEFAULT_MAX_ITEMS`
- `DEFAULT_MAX_CHARS`
- `project_metadata_filter`
- `build_session_context`
- `_query_items`
- `_raw_data_entry`
- `_item_entry`
- `_field`
- `_text`

- [ ] **Step 4: Run Claude Code SessionStart tests**

Run:

```bash
python3 -m unittest tests/test_session_context.py -v
```

Expected: PASS.

- [ ] **Step 5: Run all Claude Code integration tests**

Run:

```bash
python3 -m unittest discover -s tests
```

Expected: `Ran ... tests` and `OK`.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add memind-integrations/claude-code/scripts/lib/session_context.py memind-integrations/claude-code/tests/test_session_context.py
git commit -m "feat: compile claude code session context"
```

Expected: commit succeeds.

---

## Task 3: Wire Claude Code Prompt Retrieval to the Compiler

**Files:**

- Modify: `memind-integrations/claude-code/scripts/retrieve.py`
- Modify: `memind-integrations/claude-code/tests/test_hooks.py`
- Modify: `memind-integrations/claude-code/tests/test_context_compiler.py`

- [ ] **Step 1: Add failing prompt-retrieval compiler tests**

Append to `ContextCompilerTest` in `memind-integrations/claude-code/tests/test_context_compiler.py`:

```python
    def test_prompt_retrieval_context_groups_agent_categories_by_execution_value(self):
        from scripts.lib.context_compiler import compile_prompt_retrieval_context

        data = {
            "insights": [
                {"id": "ins-leaf", "text": "Leaf insight", "tier": "LEAF"},
                {"id": "ins-root", "text": "Root insight", "tier": "ROOT"},
            ],
            "items": [
                {"id": "tool-1", "text": "Use mvn -pl memind-server test for server checks.", "category": "tool", "finalScore": 0.6},
                {"id": "res-1", "text": "Retry spool events are cleared only after successful agent_timeline extraction.", "category": "resolution", "finalScore": 0.9},
                {"id": "pb-1", "text": "When hooks change, run both integration test suites.", "category": "playbook", "finalScore": 0.8},
                {"id": "dir-1", "text": "Do not default Claude Code or Codex to conversation rawdata.", "category": "directive", "finalScore": 0.7},
                {"id": "ev-1", "text": "rawdata-agent emits agent_episode segment metadata.", "category": "event", "finalScore": 0.5},
                {"id": "ev-high", "text": "A high-scoring general fact should not crowd out agent-specific sections.", "category": "event", "finalScore": 0.99},
            ],
        }

        rendered = compile_prompt_retrieval_context(
            data,
            {"retrieveMaxEntries": 8, "retrieveMaxChars": 6000, "retrievePromptPreamble": "Relevant memories from Memind."},
        )

        self.assertIn("<memind_memories>", rendered)
        self.assertIn("## Directives", rendered)
        self.assertIn("[item:dir-1 directive] Do not default Claude Code", rendered)
        self.assertIn("## Resolved Problems", rendered)
        self.assertIn("[item:res-1 resolution] Retry spool events", rendered)
        self.assertIn("## Agent Playbooks", rendered)
        self.assertIn("[item:pb-1 playbook] When hooks change", rendered)
        self.assertIn("## Tool Notes", rendered)
        self.assertIn("[item:tool-1 tool] Use mvn", rendered)
        self.assertIn("## Insights", rendered)
        self.assertLess(rendered.index("ins-root"), rendered.index("ins-leaf"))
        self.assertIn("## Memory Items", rendered)
        self.assertIn("[item:ev-high event] A high-scoring general fact", rendered)
        self.assertIn("[item:ev-1 event] rawdata-agent emits", rendered)
        self.assertTrue(rendered.endswith("</memind_memories>"))

    def test_prompt_retrieval_context_keeps_degraded_notice(self):
        from scripts.lib.context_compiler import compile_prompt_retrieval_context

        rendered = compile_prompt_retrieval_context(
            {"status": "degraded"},
            {"retrieveMaxEntries": 8, "retrieveMaxChars": 1000, "retrievePromptPreamble": ""},
        )

        self.assertIn("Memory retrieval encountered an error", rendered)
        self.assertIn("<memind_memories>", rendered)
```

- [ ] **Step 2: Run compiler tests**

Run:

```bash
python3 -m unittest tests/test_context_compiler.py -v
```

Expected: PASS. Task 1 already defines `compile_prompt_retrieval_context`; a failure here means the Task 1 implementation diverged from this plan and must be corrected before wiring `retrieve.py`.

- [ ] **Step 3: Update retrieve.py to use the compiler**

Modify `memind-integrations/claude-code/scripts/retrieve.py`.

Add import:

```python
from lib.context_compiler import compile_prompt_retrieval_context
```

Replace `_format_context` with:

```python
def _format_context(data, config):
    return compile_prompt_retrieval_context(data, config)
```

Delete these old local formatter symbols from `retrieve.py` after `_format_context` delegates to the compiler:

- `AGENT_CATEGORY_SECTIONS`
- `_item_category`
- `_group_agent_items`

- [ ] **Step 4: Update hook formatter tests for new section order**

In `memind-integrations/claude-code/tests/test_hooks.py`, update `test_format_context_groups_agent_memory_categories` expectations:

```python
self.assertIn("## Directives", context)
self.assertIn("## Resolved Problems", context)
self.assertIn("## Agent Playbooks", context)
self.assertIn("## Tool Notes", context)
self.assertLess(context.index("## Directives"), context.index("## Resolved Problems"))
self.assertLess(context.index("## Resolved Problems"), context.index("## Agent Playbooks"))
```

Keep degraded retrieval tests unchanged.

- [ ] **Step 5: Run Claude Code tests**

Run:

```bash
python3 -m unittest discover -s tests
```

Expected: `Ran ... tests` and `OK`.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
git add memind-integrations/claude-code/scripts/retrieve.py memind-integrations/claude-code/tests/test_hooks.py memind-integrations/claude-code/tests/test_context_compiler.py
git commit -m "feat: compile claude code prompt retrieval context"
```

Expected: commit succeeds.

---

## Task 4: Port Compiler to Codex

**Files:**

- Create: `memind-integrations/codex/scripts/lib/context_compiler.py`
- Create: `memind-integrations/codex/tests/test_context_compiler.py`

- [ ] **Step 1: Copy Claude Code compiler implementation to Codex**

Copy the final content of:

```text
memind-integrations/claude-code/scripts/lib/context_compiler.py
```

to:

```text
memind-integrations/codex/scripts/lib/context_compiler.py
```

The file must remain self-contained and import only standard library modules.

- [ ] **Step 2: Copy compiler tests to Codex and adjust product-specific strings**

Create `memind-integrations/codex/tests/test_context_compiler.py` from the Claude Code test file. It must keep the same test method names and the same behavioral assertions so Claude Code and Codex cannot drift. Use Codex-specific examples where text names the host:

```python
"Completed SessionStart context injection for Codex."
"After changing Codex hooks, run the Codex integration unittest suite and git diff --check."
```

The import must be:

```python
from scripts.lib.context_compiler import compile_session_start_context
```

Add this parity test to the Codex file so future changes keep the public compiler API aligned:

```python
    def test_compiler_exports_match_claude_code_contract(self):
        import scripts.lib.context_compiler as compiler

        self.assertTrue(callable(compiler.compile_session_start_context))
        self.assertTrue(callable(compiler.compile_prompt_retrieval_context))
```

- [ ] **Step 3: Run Codex compiler tests**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 python -m unittest tests/test_context_compiler.py -v
```

Expected: PASS.

- [ ] **Step 4: Commit Task 4**

Run:

```bash
git add memind-integrations/codex/scripts/lib/context_compiler.py memind-integrations/codex/tests/test_context_compiler.py
git commit -m "feat: add codex agent context compiler"
```

Expected: commit succeeds.

---

## Task 5: Wire Codex SessionStart and Prompt Retrieval

**Files:**

- Modify: `memind-integrations/codex/scripts/lib/session_context.py`
- Modify: `memind-integrations/codex/scripts/retrieve.py`
- Modify: `memind-integrations/codex/tests/test_session_context.py`
- Modify: `memind-integrations/codex/tests/test_hooks.py`

- [ ] **Step 1: Wire Codex session_context.py**

Modify `memind-integrations/codex/scripts/lib/session_context.py` with the same wiring used in Claude Code:

```python
from lib.context_compiler import compile_session_start_context
```

Replace `render_session_context` with:

```python
def render_session_context(context, config):
    return compile_session_start_context(context, config)
```

Delete these old rendering-only symbols from Codex `session_context.py` after `render_session_context` delegates to the compiler:

- `html`
- `SECTION_ORDER`
- `_section_entries`
- `_render_entry`
- `_truncate_lines`
- `_clean`

- [ ] **Step 2: Wire Codex retrieve.py**

Modify `memind-integrations/codex/scripts/retrieve.py`.

Add:

```python
from lib.context_compiler import compile_prompt_retrieval_context
```

Replace `_format_context` with:

```python
def _format_context(data, config):
    return compile_prompt_retrieval_context(data, config)
```

Delete these old local formatter symbols from Codex `retrieve.py` after `_format_context` delegates to the compiler:

- `AGENT_CATEGORY_SECTIONS`
- `_item_category`
- `_group_agent_items`

- [ ] **Step 3: Update Codex SessionStart tests**

In `memind-integrations/codex/tests/test_session_context.py`, update the fake rawdata to include a deterministic timestamp:

```python
types.SimpleNamespace(
    id="rd-1",
    caption="Fixed Codex agent timeline flushing.",
    metadata={},
    created_at="2026-05-27T01:00:00Z",
)
```

Then update rendered assertions to expect the improved preamble and dated rawdata citation:

```python
self.assertIn("Historical Memind project memory", rendered)
self.assertIn("Current user instructions and repository files take precedence", rendered)
self.assertIn("Verify old implementation details against the working tree", rendered)
self.assertIn("[rawdata:rd-1, 2026-05-27] Fixed Codex", rendered)
```

- [ ] **Step 4: Update Codex hook formatter tests**

In `memind-integrations/codex/tests/test_hooks.py`, update `test_format_context_groups_agent_memory_categories` with the same section-order assertions used for Claude Code:

```python
self.assertIn("## Directives", context)
self.assertIn("## Resolved Problems", context)
self.assertIn("## Agent Playbooks", context)
self.assertIn("## Tool Notes", context)
```

- [ ] **Step 5: Run Codex tests**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 python -m unittest discover -s tests
```

Expected: `Ran ... tests` and `OK`.

- [ ] **Step 6: Commit Task 5**

Run:

```bash
git add memind-integrations/codex/scripts/lib/session_context.py memind-integrations/codex/scripts/retrieve.py memind-integrations/codex/tests/test_session_context.py memind-integrations/codex/tests/test_hooks.py
git commit -m "feat: compile codex memory context"
```

Expected: commit succeeds.

---

## Task 6: Update Codex Installer and Configuration Tests

**Files:**

- Modify: `memind-integrations/codex/install.sh`
- Modify: `memind-integrations/codex/tests/test_installer.py`

- [ ] **Step 1: Add failing installer test for context compiler file**

Add this test method to `InstallerTest` in `memind-integrations/codex/tests/test_installer.py`. This must read `install.sh` directly because local `--source-root` installation copies the whole `scripts/` directory and would not catch a missing remote download list entry:

```python
    def test_remote_install_file_list_includes_context_compiler(self):
        install_script = (ROOT / "install.sh").read_text()

        self.assertIn('"scripts/lib/context_compiler.py"', install_script)
```

- [ ] **Step 2: Run installer tests to verify failure**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 python -m unittest tests/test_installer.py -v
```

Expected: FAIL because `install.sh` does not yet include `scripts/lib/context_compiler.py`.

- [ ] **Step 3: Add context_compiler.py to installer file list**

Modify `memind-integrations/codex/install.sh` in `download_remote_install()` and insert:

```bash
"scripts/lib/context_compiler.py"
```

near the other `scripts/lib/*.py` entries.

- [ ] **Step 4: Run installer tests**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 python -m unittest tests/test_installer.py -v
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

Run:

```bash
git add memind-integrations/codex/install.sh memind-integrations/codex/tests/test_installer.py
git commit -m "fix: install codex context compiler"
```

Expected: commit succeeds.

---

## Task 7: Update Documentation and Examples

**Files:**

- Modify: `memind-integrations/claude-code/README.md`
- Modify: `memind-integrations/codex/README.md`

- [ ] **Step 1: Update SessionStart context example in Claude Code README**

In `memind-integrations/claude-code/README.md`, replace the existing SessionStart context example with:

```text
<memind_session_context project="payment-service-<stable-hash>">
Historical Memind project memory. Use only when directly helpful. Current user instructions and repository files take precedence. Verify old implementation details against the working tree before relying on them.

## Continue From
- [rawdata:rd-1, 2026-05-27] Completed SessionStart context injection for Claude Code and Codex.

## Must Follow
- [item:101 directive, 2026-05-27] Keep userId and agentId stable; use metadata.projectSlug for project isolation.

## Watch Outs
- [item:102 resolution, 2026-05-27] Codex tests must run with Python 3.12; older Python can fail on modern type syntax.

## Reusable Playbooks
- [item:103 playbook, 2026-05-27] After changing Claude Code or Codex hooks, run both integration unittest suites and git diff --check.

## Useful Facts
- [item:104 event, 2026-05-27] SessionStart is read-only: it queries memory and injects context without writing rawdata.
</memind_session_context>
```

- [ ] **Step 2: Update prompt retrieval example in Claude Code README**

In the retrieval section, update the `<memind_memories>` example to show:

```text
<memind_memories>
Relevant memories from Memind. Use only when directly helpful:

## Directives
- [item:201 directive] Do not default Claude Code or Codex to conversation rawdata.

## Resolved Problems
- [item:202 resolution] Retry spool events are cleared only after successful agent_timeline extraction.

## Agent Playbooks
- [item:203 playbook] When hooks change, run both integration test suites and git diff --check.

## Tool Notes
- [item:204 tool] Use Python 3.12 for the Codex integration test suite.

## Insights
- [insight:301 root] Coding-agent integrations share memory through stable userId and agentId.

## Memory Items
- [item:205 event] rawdata-agent emits agent_episode segment metadata.
</memind_memories>
```

- [ ] **Step 3: Mirror documentation updates in Codex README**

Apply the same structure to `memind-integrations/codex/README.md`, using Codex wording where the surrounding text references the host.

- [ ] **Step 4: Commit Task 7**

Run:

```bash
git add memind-integrations/claude-code/README.md memind-integrations/codex/README.md
git commit -m "docs: explain agent context compiler output"
```

Expected: commit succeeds.

---

## Task 8: Final Verification

**Files:**

- Verify all changed files.

- [ ] **Step 1: Run Claude Code full test suite**

Run:

```bash
python3 -m unittest discover -s tests
```

from:

```text
memind-integrations/claude-code
```

Expected: `OK`.

- [ ] **Step 2: Run Codex full test suite**

Run:

```bash
UV_CACHE_DIR=.uv-cache uv run --python /opt/homebrew/bin/python3.12 python -m unittest discover -s tests
```

from:

```text
memind-integrations/codex
```

Expected: `OK`.

- [ ] **Step 3: Check whitespace**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 4: Check staged/untracked files**

Run:

```bash
git status --short --branch
```

Expected:

- Current branch remains `feat/rawdata-agent-memory`.
- No `__pycache__` or `.pyc` files are staged.
- Only intentional source, test, installer, and README changes appear.

- [ ] **Step 5: Push final branch**

Run:

```bash
git push
```

Expected: remote `feat/rawdata-agent-memory` advances successfully.

---

## Self-Review Checklist

- Spec coverage:
  - SessionStart project-continuity compiler: Tasks 1, 2, 4, 5.
  - Prompt retrieval compiler: Tasks 1, 3, 4, 5.
  - Claude Code and Codex parity: Tasks 4 and 5.
  - No Memind core/OpenAPI/rawdata-agent changes: File Structure and all tasks.
  - Installer safety for Codex: Task 6.
  - Documentation and examples: Task 7.
  - Verification: Task 8.
- Placeholder scan: no unfinished placeholder markers or unspecified implementation placeholders are present.
- Type consistency:
  - Public compiler functions are `compile_session_start_context(context, config)` and `compile_prompt_retrieval_context(data, config)`.
  - Context keys stay compatible with existing `session_context.py`: `recentRawData`, `items.directive`, `items.watchOut`, `items.playbook`, `items.fact`.
  - Existing config keys remain valid: `sessionContextMaxChars`, `retrieveMaxEntries`, `retrieveMaxChars`, `retrievePromptPreamble`.
