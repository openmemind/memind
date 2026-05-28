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

HIGH_VALUE_KINDS = {"file_edit", "command", "test_result"}
TOOL_CONTEXT_CATEGORIES = ["resolution", "tool", "playbook", "directive"]


def extract_tool_context_target(event, hook_input, project_slug):
    metadata = event.get("metadata") or {}
    target = {
        "toolName": event.get("toolName"),
        "kind": event.get("kind"),
        "path": event.get("path"),
        "command": event.get("command"),
        "operation": event.get("operation"),
        "validationType": metadata.get("validationType"),
        "projectSlug": project_slug,
        "cwd": hook_input.get("cwd"),
        "turnId": metadata.get("turnId"),
        "turnSeq": metadata.get("turnSeq"),
    }
    return {key: value for key, value in target.items() if value not in (None, "", [])}


def should_query_tool_context(target, config):
    if not config.get("autoToolContext", True) or not config.get("autoRetrieve", True):
        return False
    if not target or target.get("kind") not in HIGH_VALUE_KINDS:
        return False
    return bool(target.get("path") or target.get("command"))


def build_metadata_filter(target, include_project=True):
    all_conditions = []
    any_conditions = []
    if include_project and target.get("projectSlug"):
        all_conditions.append(
            {"path": "projectSlug", "op": "eq", "value": target["projectSlug"]}
        )
    if target.get("path"):
        any_conditions.append({"path": "files", "op": "contains", "value": target["path"]})
    if target.get("command"):
        any_conditions.append(
            {"path": "commands", "op": "contains", "value": target["command"]}
        )
    if target.get("toolName"):
        any_conditions.append(
            {"path": "toolNames", "op": "contains", "value": target["toolName"]}
        )
    return {
        "all": all_conditions,
        "any": any_conditions,
        "not": [],
    }


def current_turn_prompt(events, turn_id):
    if not turn_id:
        return ""
    for event in reversed(events or []):
        metadata = event.get("metadata") or {}
        if event.get("kind") == "user_prompt" and metadata.get("turnId") == turn_id:
            return event.get("text") or ""
    return ""


def load_tool_context(client, user_id, agent_id, target, config):
    max_items = int(config.get("toolContextMaxItems", 6))
    min_exact = int(config.get("toolContextMinExactItems", 2))
    exact_items = []

    for include_project in [True, False]:
        metadata_filter = build_metadata_filter(target, include_project=include_project)
        if not metadata_filter["any"]:
            continue
        response = client.query_items(
            user_id=user_id,
            agent_id=agent_id,
            scope=None,
            categories=TOOL_CONTEXT_CATEGORIES,
            source_clients=None,
            raw_data_types=["agent_timeline"],
            metadata_filter=metadata_filter,
            limit=max(10, max_items * 3),
        )
        exact_items.extend(_normalize_items(getattr(response, "items", []) or []))
        if len(exact_items) >= max_items:
            break

    raw_data = []
    metadata_filter = build_metadata_filter(target, include_project=bool(target.get("projectSlug")))
    if metadata_filter["any"]:
        response = client.query_raw_data(
            user_id=user_id,
            agent_id=agent_id,
            types=["agent_timeline"],
            source_clients=None,
            metadata_filter=metadata_filter,
            include={"metadata": True, "segment": False},
            limit=max(6, max_items),
        )
        raw_data = _normalize_raw_data(getattr(response, "raw_data", []) or [])

    fallback_items = []
    if len(exact_items) < min_exact:
        retrieve_response = client.retrieve(
            user_id,
            agent_id,
            _semantic_query(target),
            config.get("retrieveStrategy", "SIMPLE"),
            False,
            scope=None,
            categories=TOOL_CONTEXT_CATEGORIES,
            metadata_filter=(
                {"all": [{"path": "projectSlug", "op": "eq", "value": target["projectSlug"]}]}
                if target.get("projectSlug")
                else None
            ),
            include={"rawDataMetadata": True},
        )
        fallback_items = _normalize_items(getattr(retrieve_response, "items", []) or [])

    items = _dedupe_by_id(exact_items + fallback_items)
    return {
        "target": dict(target),
        "items": rank_items(items, target)[:max_items],
        "rawData": rank_raw_data(raw_data, target)[:max_items],
    }


def _semantic_query(target):
    parts = []
    if target.get("prompt"):
        parts.append("task: " + target["prompt"])
    if target.get("path"):
        parts.append("file: " + target["path"])
    if target.get("command"):
        parts.append("command: " + target["command"])
    if target.get("toolName"):
        parts.append("tool: " + target["toolName"])
    return "\n".join(parts) or "coding agent tool context"


def _normalize_items(items):
    result = []
    for item in items:
        result.append(
            {
                "id": _field(item, "id"),
                "text": _field(item, "text"),
                "category": str(_field(item, "category") or "memory").lower(),
                "createdAt": _field(item, "createdAt") or _field(item, "created_at"),
                "metadata": _field(item, "metadata") or {},
            }
        )
    return [item for item in result if item.get("text")]


def _normalize_raw_data(raw_data):
    result = []
    for raw in raw_data:
        result.append(
            {
                "id": _field(raw, "rawDataId") or _field(raw, "raw_data_id") or _field(raw, "id"),
                "caption": _field(raw, "caption"),
                "type": _field(raw, "type"),
                "createdAt": _field(raw, "createdAt") or _field(raw, "created_at"),
                "metadata": _field(raw, "metadata") or {},
            }
        )
    return [raw for raw in result if raw.get("caption") or raw.get("metadata")]


def rank_items(items, target):
    return sorted(
        items,
        key=lambda item: (
            _match_score(item.get("metadata") or {}, target),
            _category_score(item.get("category")),
            item.get("createdAt") or "",
            item.get("id") or "",
        ),
        reverse=True,
    )


def rank_raw_data(raw_data, target):
    return sorted(
        raw_data,
        key=lambda raw: (
            _match_score(raw.get("metadata") or {}, target),
            raw.get("createdAt") or "",
            raw.get("id") or "",
        ),
        reverse=True,
    )


def _match_score(metadata, target):
    score = 0
    if target.get("projectSlug") and metadata.get("projectSlug") == target["projectSlug"]:
        score += 3
    if target.get("path") and target["path"] in metadata.get("files", []):
        score += 10
    if target.get("command") and target["command"] in metadata.get("commands", []):
        score += 8
    if target.get("toolName") and target["toolName"] in metadata.get("toolNames", []):
        score += 3
    stats = metadata.get("toolStats") or {}
    if target.get("toolName") in stats:
        tool_stats = stats[target["toolName"]]
        score += int(tool_stats.get("successCount") or 0)
    return score


def _category_score(category):
    return {"resolution": 5, "tool": 4, "playbook": 3, "directive": 2}.get(category or "", 1)


def _dedupe_by_id(items):
    result = []
    seen = set()
    for item in items:
        key = item.get("id") or item.get("text")
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)
