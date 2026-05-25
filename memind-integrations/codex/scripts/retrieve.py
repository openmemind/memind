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


import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib.client import MemindClient
from lib.agent_timeline import normalize_user_prompt_event
from lib.config import load_config
from lib.content import read_recent_context
from lib.identity import resolve_identity
from lib.logging_utils import debug_log
from lib.state import SessionStateStore, state_key
from ingest import state_root


AGENT_CATEGORY_SECTIONS = [
    ("playbook", "## Agent Playbooks"),
    ("resolution", "## Resolved Problems"),
    ("tool", "## Tool Notes"),
    ("directive", "## Directives"),
]


def _format_context(data, config):
    max_entries = int(config.get("retrieveMaxEntries", 8))
    max_chars = int(config.get("retrieveMaxChars", 6000))
    tier_rank = {"ROOT": 0, "BRANCH": 1, "LEAF": 2}

    insights = [insight for insight in (data.get("insights") or []) if insight.get("text")]
    insights.sort(key=lambda insight: (tier_rank.get(str(insight.get("tier", "LEAF")).upper(), 2), str(insight.get("id", ""))))
    high_level = [insight for insight in insights if str(insight.get("tier", "")).upper() in {"ROOT", "BRANCH"}]
    selected_insights = (high_level or insights)[: min(3, max_entries)]

    remaining = max_entries - len(selected_insights)
    items = [item for item in (data.get("items") or []) if item.get("text")]
    items.sort(
        key=lambda item: item.get("finalScore") if item.get("finalScore") is not None else item.get("vectorScore", 0),
        reverse=True,
    )
    selected_items = items[: max(0, remaining)]

    sections = []
    if selected_insights:
        sections.append("## Insights")
        sections.extend(f"- [insight:{insight.get('id')}] {insight.get('text')}" for insight in selected_insights)
    grouped_agent_items = _group_agent_items(selected_items)
    for category, header in AGENT_CATEGORY_SECTIONS:
        category_items = grouped_agent_items.get(category, [])
        if category_items:
            if sections:
                sections.append("")
            sections.append(header)
            sections.extend(f"- [item:{item.get('id')}] {item.get('text')}" for item in category_items)
    general_items = [item for item in selected_items if _item_category(item) not in grouped_agent_items]
    if general_items:
        if sections:
            sections.append("")
        sections.append("## Memory Items")
        sections.extend(f"- [item:{item.get('id')}] {item.get('text')}" for item in general_items)
    degraded_notice = ""
    if data.get("status") == "degraded":
        degraded_notice = "\n[Note: Memory retrieval encountered an error. Results may be incomplete.]\n"
    if not sections and not degraded_notice:
        return ""
    body = "\n".join(sections)[:max_chars]
    return f"<memind_memories>\n{config.get('retrievePromptPreamble') or ''}\n{body}{degraded_notice}\n</memind_memories>"


def _item_category(item):
    return str(item.get("category") or "").strip().lower()


def _group_agent_items(items):
    agent_categories = {category for category, _header in AGENT_CATEGORY_SECTIONS}
    grouped = {}
    for item in items:
        category = _item_category(item)
        if category in agent_categories:
            grouped.setdefault(category, []).append(item)
    return grouped


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        config = load_config()
        prompt = hook_input.get("prompt") or hook_input.get("user_prompt") or ""
        hook_input["source_client"] = config.get("sourceClient") or "codex"
        session_key = state_key(hook_input)
        with SessionStateStore(state_root()).locked(session_key) as state:
            turn_id, turn_seq = state.start_agent_turn(session_key)
            seq = state.next_agent_seq()
            state.append_agent_event(
                normalize_user_prompt_event(
                    hook_input, seq, turn_id=turn_id, turn_seq=turn_seq
                )
            )
        if not config.get("autoRetrieve", True):
            print(json.dumps({"continue": True}))
            return
        identity = resolve_identity(config, hook_input)
        context_turns = int(config.get("retrieveContextTurns", 0))
        recent_context = read_recent_context(hook_input.get("transcript_path"), context_turns)
        query = prompt if not recent_context else f"{recent_context}\ncurrent: {prompt}"
        client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=12, max_retries=0)
        result = client.retrieve(identity["userId"], identity["agentId"], query, config.get("retrieveStrategy", "SIMPLE"), False)
        context = _format_context(result.model_dump(by_alias=True), config)
        if not context:
            print(json.dumps({"continue": True}))
            return
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": context}}))
    except Exception as exc:
        try:
            debug_log(load_config(), "retrieve_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
