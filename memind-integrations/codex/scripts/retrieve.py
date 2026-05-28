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
from lib.context_compiler import compile_prompt_retrieval_context
from lib.content import read_recent_context
from lib.identity import resolve_identity
from lib.logging_utils import debug_log
from lib.state import SessionStateStore, state_key
from ingest import state_root


def _format_context(data, config):
    return compile_prompt_retrieval_context(data, config)


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
