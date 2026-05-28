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
import re
from pathlib import Path


class AgentTimelineState:
    def __init__(self, root, max_events=500):
        self.root = Path(root)
        self.max_events = max_events

    def append(self, session_id, event):
        if not event or not event.get("eventId"):
            return
        state = self._read(session_id)
        if any(existing.get("eventId") == event["eventId"] for existing in state["events"]):
            return
        events = sorted([*state["events"], event], key=_event_key)
        if len(events) > self.max_events:
            events = events[-self.max_events :]
        self._write(session_id, {"version": 1, "events": events})

    def list(self, session_id):
        return sorted(self._read(session_id)["events"], key=_event_key)

    def clear(self, session_id, event_ids=None):
        if not event_ids:
            self._write(session_id, {"version": 1, "events": []})
            return
        submitted = set(event_ids)
        events = [event for event in self.list(session_id) if event.get("eventId") not in submitted]
        self._write(session_id, {"version": 1, "events": events})

    def next_seq(self, session_id):
        events = self.list(session_id)
        if not events:
            return 1
        return max(int(event.get("seq") or 0) for event in events) + 1

    def _read(self, session_id):
        try:
            parsed = json.loads(self._path(session_id).read_text())
            if parsed.get("version") == 1 and isinstance(parsed.get("events"), list):
                return {"version": 1, "events": [event for event in parsed["events"] if event.get("eventId")]}
        except Exception:
            pass
        return {"version": 1, "events": []}

    def _write(self, session_id, state):
        self.root.mkdir(parents=True, exist_ok=True)
        self._path(session_id).write_text(json.dumps(state, indent=2, default=str) + "\n")

    def _path(self, session_id):
        return self.root / f"{_safe_key(session_id)}.json"


def _safe_key(value):
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", str(value or "default")) or "default"


def _event_key(event):
    return (int(event.get("seq") or 0), str(event.get("eventId") or ""))
