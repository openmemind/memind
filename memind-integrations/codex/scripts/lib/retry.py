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
        temp = path.with_suffix(".tmp")
        temp.write_text(json.dumps(payload, ensure_ascii=False, sort_keys=True))
        temp.chmod(0o600)
        temp.replace(path)
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
        try:
            path.replace(path.with_suffix(".json"))
        except OSError:
            pass

    def cleanup(self, max_files, max_age_days):
        cutoff = time.time() - max_age_days * 86400
        for path in self._payload_files():
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
