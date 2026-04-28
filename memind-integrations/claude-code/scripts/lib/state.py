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
import fcntl
import json
import re
import time
from pathlib import Path

SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9_.-]+")


def _safe_session_id(session_id):
    return SAFE_NAME_RE.sub("_", session_id or "unknown-session")[:128]


class SessionState:
    def __init__(self, data):
        self.data = data
        self.data.setdefault("submitted", [])

    def is_submitted(self, fingerprint):
        return fingerprint in set(self.data.get("submitted", []))

    def mark_submitted(self, fingerprints):
        submitted = set(self.data.get("submitted", []))
        submitted.update(fingerprints)
        self.data["submitted"] = sorted(submitted)
        self.data["updatedAt"] = time.time()


class SessionStateStore:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def _path(self, session_id):
        return self.root / f"{_safe_session_id(session_id)}.json"

    def _lock_path(self, session_id):
        return self.root / f"{_safe_session_id(session_id)}.lock"

    @contextlib.contextmanager
    def locked(self, session_id):
        path = self._path(session_id)
        lock_path = self._lock_path(session_id)
        with lock_path.open("a+") as lock_handle:
            fcntl.flock(lock_handle, fcntl.LOCK_EX)
            try:
                if path.exists():
                    data = json.loads(path.read_text())
                else:
                    data = {}
                state = SessionState(data)
                yield state
                temp = path.with_suffix(".tmp")
                temp.write_text(json.dumps(state.data, sort_keys=True))
                temp.replace(path)
            finally:
                fcntl.flock(lock_handle, fcntl.LOCK_UN)

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
