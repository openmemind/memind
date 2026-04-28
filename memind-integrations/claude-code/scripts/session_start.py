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


def main():
    config = load_config()
    try:
        client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=2)
        try:
            client.health()
        except Exception:
            _tcp_check(config["memindApiUrl"], timeout=1)
        claimed = None
        try:
            spool = RetrySpool(retry_root())
            claimed = spool.claim_next()
            if claimed:
                payload = spool.load_claimed(claimed)
                replay_client = MemindClient(config["memindApiUrl"], config.get("memindApiToken"), timeout=10)
                if payload.get("kind") == "add-message":
                    replay_client.add_message(payload["userId"], payload["agentId"], payload["message"])
                    if payload.get("sessionId") and payload.get("fingerprint"):
                        with SessionStateStore(state_root()).locked(payload["sessionId"]) as state:
                            state.mark_submitted([payload["fingerprint"]])
                    spool.complete(claimed)
                elif payload.get("kind") == "commit":
                    replay_client.commit(payload["userId"], payload["agentId"])
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
    except Exception as exc:
        debug_log(config, "session_start_health_failed", {"error": str(exc)})
    print(json.dumps({"continue": True, "suppressOutput": True}))


if __name__ == "__main__":
    main()
