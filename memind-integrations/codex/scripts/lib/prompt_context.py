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


def project_metadata_filter(project_slug):
    return {"all": [{"path": "projectSlug", "op": "eq", "value": project_slug}]}


def build_prompt_context(client, identity, query, project_slug, config):
    project_data = _retrieve(
        client,
        identity,
        query,
        config,
        metadata_filter=project_metadata_filter(project_slug) if project_slug else None,
    )
    _mark_sources(project_data, project_slug, default_source="project")

    project_count = _usable_entry_count(project_data, ("items", "insights"))
    min_entries = int(config.get("promptContextProjectMinEntries", 4))
    fallback_limit = int(config.get("promptContextGlobalFallbackEntries", 3))

    if project_count >= min_entries or fallback_limit <= 0:
        return _shape(project_data, project_slug)

    fallback_data = _retrieve(client, identity, query, config, metadata_filter=None)
    _mark_sources(fallback_data, project_slug)
    project_keys = _entry_keys(project_data)
    fallback_data = _filter_fallback(fallback_data, config, fallback_limit, project_keys)

    return _shape(_merge(project_data, fallback_data), project_slug)


def _retrieve(client, identity, query, config, metadata_filter=None):
    response = client.retrieve(
        identity["userId"],
        identity["agentId"],
        query,
        config.get("retrieveStrategy", "SIMPLE"),
        False,
        metadata_filter=metadata_filter,
        include={"raw_data_metadata": True},
    )
    return _dump_response(response)


def _dump_response(response):
    if isinstance(response, dict):
        data = response
    elif hasattr(response, "model_dump"):
        data = response.model_dump(by_alias=True)
    else:
        data = {
            "items": list(getattr(response, "items", []) or []),
            "insights": list(getattr(response, "insights", []) or []),
            "rawData": list(getattr(response, "raw_data", []) or getattr(response, "rawData", []) or []),
        }

    return {
        "items": [_dump_entry(entry) for entry in data.get("items", []) or []],
        "insights": [_dump_entry(entry) for entry in data.get("insights", []) or []],
        "rawData": [_dump_entry(entry) for entry in data.get("rawData", []) or data.get("raw_data", []) or []],
    }


def _dump_entry(entry):
    if isinstance(entry, dict):
        return dict(entry)
    if hasattr(entry, "model_dump"):
        return entry.model_dump(by_alias=True)
    return {
        key: value
        for key, value in vars(entry).items()
        if not key.startswith("_")
    }


def _shape(data, project_slug):
    return {
        "projectSlug": project_slug,
        "mode": "project-first",
        "items": data.get("items") or [],
        "insights": data.get("insights") or [],
        "rawData": data.get("rawData") or data.get("raw_data") or [],
    }


def _merge(project_data, fallback_data):
    return {
        "items": _dedupe((project_data.get("items") or []) + (fallback_data.get("items") or [])),
        "insights": _dedupe((project_data.get("insights") or []) + (fallback_data.get("insights") or [])),
        "rawData": _dedupe((project_data.get("rawData") or []) + (fallback_data.get("rawData") or [])),
    }


def _filter_fallback(data, config, limit, excluded_keys=None):
    min_score = float(config.get("promptContextGlobalFallbackMinScore", 0.65))
    excluded_keys = excluded_keys or set()
    return {
        "items": _take_fallback_entries(data.get("items", []), min_score, limit, excluded_keys),
        "insights": _take_fallback_entries(data.get("insights", []), min_score, limit, excluded_keys),
        "rawData": [],
    }


def _take_fallback_entries(entries, min_score, limit, excluded_keys):
    kept = []
    seen = set()
    for entry in entries:
        key = _entry_key(entry)
        if not key or key in excluded_keys or key in seen:
            continue
        if entry.get("memindContextSource") == "project":
            continue
        if not _passes_fallback_score(entry, min_score):
            continue
        kept.append(entry)
        seen.add(key)
        if len(kept) >= limit:
            break
    return kept


def _usable_entry_count(data, buckets):
    count = 0
    for bucket in buckets:
        for entry in data.get(bucket) or []:
            if _field(entry, "text"):
                count += 1
    return count


def _entry_keys(data):
    keys = set()
    for bucket in ("items", "insights", "rawData", "raw_data"):
        for entry in data.get(bucket) or []:
            key = _entry_key(entry)
            if key:
                keys.add(key)
    return keys


def _mark_sources(data, project_slug, default_source=None):
    for key in ("items", "insights", "rawData", "raw_data"):
        for entry in data.get(key) or []:
            entry["memindContextSource"] = _source_for(entry, project_slug, default_source)


def _source_for(entry, project_slug, default_source=None):
    metadata = _field(entry, "metadata") or {}
    entry_project = metadata.get("projectSlug") if isinstance(metadata, dict) else None
    if entry_project and project_slug and entry_project == project_slug:
        return "project"
    if entry_project:
        return "shared"
    if default_source:
        return default_source
    return "global"


def _dedupe(entries):
    result = []
    seen = set()
    for entry in entries:
        key = _entry_key(entry)
        if not key or key in seen:
            continue
        seen.add(key)
        result.append(entry)
    return result


def _entry_key(entry):
    entry_id = _field(entry, "id") or _field(entry, "rawDataId") or _field(entry, "raw_data_id")
    if entry_id:
        return f"id:{entry_id}"
    text = " ".join(str(_field(entry, "text") or _field(entry, "caption") or "").lower().split())
    return f"text:{text[:260]}" if text else ""


def _passes_fallback_score(entry, min_score):
    score = _score(entry)
    if score is None:
        return True
    return score >= min_score


def _score(entry):
    for key in ("finalScore", "final_score", "vectorScore", "vector_score", "score"):
        value = _field(entry, key)
        if value is None:
            continue
        try:
            return float(value)
        except (TypeError, ValueError):
            continue
    return None


def _field(value, name):
    if isinstance(value, dict):
        return value.get(name)
    return getattr(value, name, None)
