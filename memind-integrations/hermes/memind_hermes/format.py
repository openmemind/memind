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


TIER_RANK = {"ROOT": 0, "BRANCH": 1, "LEAF": 2}


def _as_dict(data):
    if hasattr(data, "model_dump"):
        return data.model_dump(by_alias=True)
    return data or {}


def _score(item):
    if item.get("finalScore") is not None:
        return item.get("finalScore")
    return item.get("vectorScore") or 0


def format_memind_context(data, config):
    payload = _as_dict(data)
    max_entries = int(config.get("retrieveMaxEntries", 8))
    max_chars = int(config.get("retrieveMaxChars", 6000))
    preamble = config.get("retrievePromptPreamble") or ""

    insights = [insight for insight in payload.get("insights", []) if insight.get("text")]
    insights.sort(key=lambda insight: (TIER_RANK.get(str(insight.get("tier", "LEAF")).upper(), 2), str(insight.get("id", ""))))
    high_level = [insight for insight in insights if str(insight.get("tier", "")).upper() in {"ROOT", "BRANCH"}]
    selected_insights = (high_level or insights)[: min(3, max_entries)]

    remaining = max_entries - len(selected_insights)
    items = [item for item in payload.get("items", []) if item.get("text")]
    items.sort(key=_score, reverse=True)
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
    if not sections and payload.get("status") != "degraded":
        return ""

    body = "\n".join(sections)
    if payload.get("status") == "degraded":
        body += "\n[Note: Memory retrieval encountered an error. Results may be incomplete.]"
    if len(body) > max_chars:
        suffix = "\n[truncated]"
        body = f"{body[: max(0, max_chars - len(suffix))].rstrip()}{suffix}"
    return f"<memind_memories>\n{preamble}\n{body}\n</memind_memories>"
