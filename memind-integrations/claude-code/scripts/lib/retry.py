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
import time
import uuid
from pathlib import Path


class RetrySpool:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def enqueue(self, payload):
        path = self.root / f"{time.time_ns()}-{uuid.uuid4().hex}.json"
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, sort_keys=True))
        tmp.chmod(0o600)
        tmp.replace(path)
        return path

    def _payload_files(self):
        return sorted(self.root.glob("*.json"), key=lambda path: path.stat().st_mtime)

    def claim_next(self):
        for path in self._payload_files():
            claimed = path.with_suffix(".claimed")
            try:
                path.replace(claimed)
                return claimed
            except OSError:
                continue
        return None

    def load_claimed(self, path):
        return json.loads(Path(path).read_text())

    def complete(self, path):
        try:
            Path(path).unlink()
        except FileNotFoundError:
            pass

    def release(self, path):
        path = Path(path)
        if not path.exists():
            return
        target = path.with_suffix(".json")
        try:
            path.replace(target)
        except OSError:
            pass

    def cleanup(self, max_files, max_age_days):
        cutoff = time.time() - max_age_days * 86400
        files = self._payload_files()
        for path in files:
            try:
                if path.stat().st_mtime < cutoff:
                    path.unlink()
            except OSError:
                pass
        files = self._payload_files()
        for path in files[: max(0, len(files) - max_files)]:
            try:
                path.unlink()
            except OSError:
                pass
