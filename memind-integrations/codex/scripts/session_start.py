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
from lib.logging_utils import debug_log
from lib.retry import RetrySpool
from lib.state import SessionStateStore


def _tcp_check(url, timeout=1):
    parsed = urlparse(url)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    with socket.create_connection((host, port), timeout=timeout):
        return True


async def _replay_ingestion_batch(client, payload):
    session_key = payload.get("sessionKey")
    fingerprints = payload.get("fingerprints") or []
    operations = payload.get("operations") or []
    store = SessionStateStore(state_root())
    appended = 0
    for index, operation in enumerate(operations):
        if operation.get("kind") != "add-message":
            continue
        fingerprint = fingerprints[index] if index < len(fingerprints) else None
        if fingerprint and session_key:
            with store.locked(session_key) as state:
                if state.is_submitted(fingerprint):
                    continue
        await client.add_message(
            operation["userId"],
            operation["agentId"],
            operation["message"],
            operation.get("sourceClient"),
        )
        appended += 1
        if fingerprint and session_key:
            store.mark_submitted(session_key, [fingerprint])
    if appended and payload.get("commitOnSuccess"):
        await client.commit(payload["userId"], payload["agentId"], payload.get("sourceClient"))
    return appended


async def _replay_payload(client, payload):
    kind = payload.get("kind")
    if kind == "extract":
        response = await client.extract(
            payload["userId"],
            payload["agentId"],
            payload["rawContent"],
            payload.get("sourceClient"),
        )
        status = getattr(response, "status", None)
        if status != "SUCCESS":
            raise RuntimeError(f"extract replay did not fully succeed: {status}")
        session_key = payload.get("sessionKey")
        fingerprints = payload.get("fingerprints") or []
        if session_key and fingerprints:
            SessionStateStore(state_root()).mark_submitted(session_key, fingerprints)
        return len(fingerprints)
    if kind == "ingestion-batch":
        return await _replay_ingestion_batch(client, payload)
    if kind == "commit":
        await client.commit(payload["userId"], payload["agentId"], payload.get("sourceClient"))
        return 0
    return 0


async def run_session_start_async(config):
    try:
        client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=2, max_retries=0)
        try:
            await client.health()
        except Exception:
            _tcp_check(config["memindApiUrl"], timeout=1)
    except Exception as exc:
        debug_log(config, "session_start_health_failed", {"error": str(exc)})
        return

    spool = RetrySpool(retry_root())
    claimed = None
    try:
        claimed = spool.claim_next()
        if claimed:
            payload = spool.load_claimed(claimed)
            replay_client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10, max_retries=0)
            await _replay_payload(replay_client, payload)
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


def run_session_start(config):
    asyncio.run(run_session_start_async(config))


def main():
    try:
        config = load_config()
        run_session_start(config)
    except Exception as exc:
        try:
            debug_log(load_config(), "session_start_failed", {"error": str(exc)})
        except Exception:
            pass
    print(json.dumps({"continue": True, "suppressOutput": True}))


if __name__ == "__main__":
    main()
