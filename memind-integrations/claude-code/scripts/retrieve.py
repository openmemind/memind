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

from lib.client import MemindClient
from lib.agent_timeline import normalize_user_prompt_event
from lib.config import load_config
from lib.context_compiler import compile_prompt_retrieval_context
from lib.content import read_recent_context
from lib.identity import project_slug, resolve_identity
from lib.logging_utils import debug_log
from lib.prompt_context import build_prompt_context
from lib.state import SessionStateStore
from ingest import state_root


def _format_context(data, config):
    return compile_prompt_retrieval_context(data, config)


def handle_user_prompt_submit(hook_input):
    config = load_config()
    session_id = hook_input.get("session_id") or "unknown-session"
    prompt = hook_input.get("prompt") or ""
    hook_input["source_client"] = config.get("sourceClient") or "claude-code"
    with SessionStateStore(state_root()).locked(session_id) as state:
        turn_id, turn_seq = state.start_agent_turn(session_id)
        seq = state.next_agent_seq()
        state.append_agent_event(
            normalize_user_prompt_event(
                hook_input, seq, turn_id=turn_id, turn_seq=turn_seq
            )
        )

    if not config.get("autoRetrieve", True):
        return {"continue": True}
    if not config.get("autoPromptContext", False):
        return {"continue": True}

    identity = resolve_identity(config, hook_input)
    context_turns = int(config.get("retrieveContextTurns", 0))
    recent_context = read_recent_context(hook_input.get("transcript_path"), context_turns)
    query = prompt if not recent_context else f"{recent_context}\ncurrent: {prompt}"
    cwd = hook_input.get("cwd") or os.getcwd()
    slug = project_slug(Path(cwd))
    client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=12, max_retries=0)
    result = build_prompt_context(client, identity, query, slug, config)
    context = _format_context(result, config)
    if not context:
        return {"continue": True}
    return {"hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": context}}


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        print(json.dumps(handle_user_prompt_submit(hook_input)))
    except Exception as exc:
        try:
            debug_log(load_config(), "retrieve_failed", {"error": str(exc)})
        except Exception:
            pass
        print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
