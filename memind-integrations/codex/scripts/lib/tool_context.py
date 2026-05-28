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
