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

import contextlib
import hashlib
import json
import os
import re
import time
from pathlib import Path

SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9_.-]+")
MAX_AGENT_EVENTS = 500


def _hash(value):
    return hashlib.sha1(value.encode("utf-8")).hexdigest()[:12]


def _safe_name(value):
    return SAFE_NAME_RE.sub("_", value or "unknown-session")[:128]


def state_key(hook_input):
    session_id = hook_input.get("session_id")
    if session_id:
        return _safe_name(session_id)
    cwd = hook_input.get("cwd")
    if cwd:
        return f"cwd-{_hash(str(Path(cwd).expanduser().resolve()))}"
    return "unknown-session"


class _FileLock:
    def __init__(self, path):
        self.path = Path(path)
        self.handle = None
        self.backend = None

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.handle = self.path.open("a+")
        try:
            import fcntl

            fcntl.flock(self.handle, fcntl.LOCK_EX)
            self.backend = "fcntl"
        except Exception:
            try:
                import msvcrt

                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_LOCK, 1)
                self.backend = "msvcrt"
            except Exception:
                self.backend = "none"
        return self

    def __exit__(self, exc_type, exc, tb):
        try:
            if self.backend == "fcntl":
                import fcntl

                fcntl.flock(self.handle, fcntl.LOCK_UN)
            elif self.backend == "msvcrt":
                import msvcrt

                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_UNLCK, 1)
        finally:
            self.handle.close()
        return False


class SessionState:
    def __init__(self, data):
        self.data = data
        self.data.setdefault("agentEvents", [])
        self.data.setdefault("nextAgentSeq", 1)
        self.data.setdefault("nextAgentTurnSeq", 1)

    def append_agent_event(self, event):
        events = list(self.data.get("agentEvents", []))
        event_id = event.get("eventId")
        if event_id and any(existing.get("eventId") == event_id for existing in events):
            return
        events.append(event)
        if len(events) > MAX_AGENT_EVENTS:
            dropped = len(events) - MAX_AGENT_EVENTS
            events = events[-MAX_AGENT_EVENTS:]
            self.data["agentEventsTruncated"] = True
            self.data["agentEventsDropped"] = int(self.data.get("agentEventsDropped", 0)) + dropped
        self.data["agentEvents"] = events
        self.data["updatedAt"] = time.time()

    def agent_events(self):
        return list(self.data.get("agentEvents", []))

    def clear_agent_events(self, event_ids):
        event_ids = set(event_ids or [])
        if not event_ids:
            return
        self.data["agentEvents"] = [
            event
            for event in self.data.get("agentEvents", [])
            if event.get("eventId") not in event_ids
        ]
        self.data["updatedAt"] = time.time()

    def next_agent_seq(self):
        seq = int(self.data.get("nextAgentSeq", 1))
        self.data["nextAgentSeq"] = seq + 1
        self.data["updatedAt"] = time.time()
        return seq

    def current_agent_turn(self):
        turn_id = self.data.get("currentAgentTurnId")
        turn_seq = self.data.get("currentAgentTurnSeq")
        if not turn_id or turn_seq is None:
            return None, None
        return turn_id, int(turn_seq)

    def start_agent_turn(self, session_key):
        turn_seq = int(self.data.get("nextAgentTurnSeq", 1))
        self.data["nextAgentTurnSeq"] = turn_seq + 1
        turn_id = f"{_safe_name(session_key)}-turn-{turn_seq}"
        self.data["currentAgentTurnId"] = turn_id
        self.data["currentAgentTurnSeq"] = turn_seq
        self.data["updatedAt"] = time.time()
        return turn_id, turn_seq

    def ensure_agent_turn(self, session_key):
        turn_id, turn_seq = self.current_agent_turn()
        if turn_id:
            return turn_id, turn_seq
        return self.start_agent_turn(session_key)

    def close_agent_turn(self, turn_id=None):
        current_turn_id = self.data.get("currentAgentTurnId")
        if turn_id is None or turn_id == current_turn_id:
            self.data.pop("currentAgentTurnId", None)
            self.data.pop("currentAgentTurnSeq", None)
            self.data["updatedAt"] = time.time()

    def is_empty(self):
        return (
            not self.data.get("agentEvents")
            and not self.data.get("currentAgentTurnId")
            and not self.data.get("currentAgentTurnSeq")
        )


class SessionStateStore:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def _path(self, session_key):
        return self.root / f"{_safe_name(session_key)}.json"

    def _lock_path(self, session_key):
        return self.root / f"{_safe_name(session_key)}.lock"

    def _read(self, path):
        if not path.exists():
            return {}
        try:
            return json.loads(path.read_text())
        except (OSError, json.JSONDecodeError):
            return {}

    def _write(self, path, data):
        temp = path.with_suffix(f".{os.getpid()}.tmp")
        temp.write_text(json.dumps(data, sort_keys=True))
        temp.replace(path)

    @contextlib.contextmanager
    def locked(self, session_key):
        path = self._path(session_key)
        with _FileLock(self._lock_path(session_key)):
            state = SessionState(self._read(path))
            yield state
            self._write(path, state.data)

    def clear_agent_events(self, session_key, event_ids):
        with self.locked(session_key) as state:
            state.clear_agent_events(event_ids)

    def cleanup(self, max_age_days):
        cutoff = time.time() - max_age_days * 86400
        removed = 0
        for path in self.root.glob("*.json"):
            try:
                if path.stat().st_mtime < cutoff:
                    path.unlink()
                    removed += 1
            except OSError:
                continue
        return removed
