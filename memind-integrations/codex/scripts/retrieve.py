#!/usr/bin/env python3

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib.client import MemindClient
from lib.config import load_config
from lib.content import read_recent_context
from lib.identity import resolve_identity
from lib.logging_utils import debug_log


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
    if selected_items:
        if sections:
            sections.append("")
        sections.append("## Memory Items")
        sections.extend(f"- [item:{item.get('id')}] {item.get('text')}" for item in selected_items)
    if not sections:
        return ""
    body = "\n".join(sections)[:max_chars]
    return f"<memind_memories>\n{config.get('retrievePromptPreamble')}\n{body}\n</memind_memories>"


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        config = load_config()
        if not config.get("autoRetrieve", True):
            print(json.dumps({"continue": True}))
            return
        identity = resolve_identity(config, hook_input)
        prompt = hook_input.get("prompt") or hook_input.get("user_prompt") or ""
        context_turns = int(config.get("retrieveContextTurns", 0))
        recent_context = read_recent_context(hook_input.get("transcript_path"), context_turns)
        query = prompt if not recent_context else f"{recent_context}\ncurrent: {prompt}"
        client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=12)
        envelope = client.retrieve(identity["userId"], identity["agentId"], query, config.get("retrieveStrategy", "SIMPLE"), False)
        context = _format_context(envelope.get("data") or {}, config)
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
