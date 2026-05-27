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

import asyncio
import json
import os
import socket
import sys
from urllib.parse import urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ingest import retry_root, state_root
from lib.client import MemindClient
from lib.config import load_config
from lib.identity import project_slug, resolve_identity
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.session_context import build_session_context, render_session_context
from lib.state import SessionStateStore


def _is_agent_timeline_extract(payload):
    raw_content = payload.get("rawContent") if isinstance(payload, dict) else None
    return payload.get("kind") == "extract" and raw_content and raw_content.get("type") == "agent_timeline"


def _tcp_check(url, timeout=1):
    parsed = urlparse(url)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    with socket.create_connection((host, port), timeout=timeout):
        return True


def _base_output():
    return {"continue": True, "suppressOutput": True}


def _context_output(client, config, hook_input):
    if not config.get("autoSessionContext", True):
        return None
    cwd = hook_input.get("cwd") or os.getcwd()
    identity = resolve_identity(config, hook_input)
    slug = project_slug(cwd)
    context = build_session_context(client, identity, slug, config)
    rendered = render_session_context(context, config)
    if not rendered:
        return None
    return {
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": rendered,
        }
    }


async def _run_session_start_async(config, hook_input=None):
    hook_input = hook_input or {}
    output = _base_output()
    try:
        client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=2, max_retries=0)
        try:
            await client.health()
        except Exception:
            _tcp_check(config["memindApiUrl"], timeout=1)
        claimed = None
        try:
            spool = RetrySpool(retry_root())
            spool.recover_orphaned_claims()
            claimed = spool.claim_next()
            if claimed:
                payload = spool.load_claimed(claimed)
                replay_client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10, max_retries=0)
                if _is_agent_timeline_extract(payload):
                    response = await replay_client.extract(
                        payload["userId"],
                        payload["agentId"],
                        payload["rawContent"],
                        payload.get("sourceClient"),
                    )
                    status = getattr(response, "status", None)
                    if status != "SUCCESS":
                        raise RuntimeError(f"extract replay did not fully succeed: {status}")
                    if payload.get("sessionId") and payload.get("eventIds"):
                        with SessionStateStore(state_root()).locked(payload["sessionId"]) as state:
                            state.clear_agent_events(payload["eventIds"])
                    spool.complete(claimed)
                elif payload.get("kind") == "commit":
                    await replay_client.commit(payload["userId"], payload["agentId"], payload.get("sourceClient"))
                    spool.complete(claimed)
                else:
                    spool.complete(claimed)
            spool.cleanup(int(config.get("ingestRetryMaxFiles", 20)), int(config.get("ingestRetryMaxAgeDays", 7)))
        except Exception as exc:
            try:
                if claimed:
                    spool.release(claimed)
            except Exception:
                pass
            debug_log(config, "retry_replay_failed", {"error": str(exc)})
        try:
            SessionStateStore(state_root()).cleanup(int(config.get("stateMaxAgeDays", 14)))
        except Exception as exc:
            debug_log(config, "state_cleanup_failed", {"error": str(exc)})
        try:
            context_output = _context_output(client, config, hook_input)
            if context_output:
                output.update(context_output)
        except Exception as exc:
            debug_log(config, "session_context_failed", {"error": str(exc)})
    except Exception as exc:
        debug_log(config, "session_start_health_failed", {"error": str(exc)})
    return output


def run_session_start(config, hook_input=None):
    return asyncio.run(_run_session_start_async(config, hook_input))


def main():
    config = load_config()
    hook_input = {}
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
    except Exception:
        hook_input = {}
    try:
        output = run_session_start(config, hook_input)
    except Exception as exc:
        debug_log(config, "session_start_health_failed", {"error": str(exc)})
        output = _base_output()
    print(json.dumps(output))


if __name__ == "__main__":
    main()
