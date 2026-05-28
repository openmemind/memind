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
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ingest import state_root
from lib.agent_timeline import normalize_hook_event
from lib.client import MemindClient
from lib.config import load_config
from lib.context_compiler import compile_tool_context
from lib.identity import project_slug, resolve_identity
from lib.logging_utils import debug_log
from lib.state import SessionStateStore
from lib.tool_context import (
    current_turn_prompt,
    extract_tool_context_target,
    load_tool_context,
    should_query_tool_context,
)


def handle_pre_tool_use(hook_input):
    config = load_config()
    session_id = hook_input.get("session_id") or "unknown-session"
    hook_input["source_client"] = config.get("sourceClient") or "claude-code"
    with SessionStateStore(state_root()).locked(session_id) as state:
        turn_id, turn_seq = state.ensure_agent_turn(session_id)
        seq = state.next_agent_seq()
        event = normalize_hook_event(hook_input, seq, turn_id=turn_id, turn_seq=turn_seq)
        state.append_agent_event(event)
        events = state.agent_events()

    cwd = hook_input.get("cwd")
    slug = project_slug(Path(cwd)) if cwd else None
    target = extract_tool_context_target(event, hook_input, slug)
    target["prompt"] = current_turn_prompt(events, target.get("turnId"))
    if not should_query_tool_context(target, config):
        return {"continue": True}

    identity = resolve_identity(config, hook_input)
    client = MemindClient(
        config["memindApiUrl"],
        config.get("memindApiToken"),
        timeout=2,
        max_retries=0,
    )
    context_input = load_tool_context(
        client,
        identity["userId"],
        identity["agentId"],
        target,
        config,
    )
    context = compile_tool_context(context_input, config)
    if not context:
        return {"continue": True}
    return {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "additionalContext": context,
        }
    }


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        print(json.dumps(handle_pre_tool_use(hook_input)))
    except Exception as exc:
        try:
            debug_log(load_config(), "pre_tool_use_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
