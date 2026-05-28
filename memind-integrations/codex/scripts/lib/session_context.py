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

from lib.context_compiler import compile_session_start_context

DEFAULT_RECENT_SESSIONS = 3
DEFAULT_MAX_ITEMS = 6
DEFAULT_MAX_CHARS = 6000

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
    return compile_session_start_context(context, config)


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


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)


def _text(item):
    return _field(item, "text")

