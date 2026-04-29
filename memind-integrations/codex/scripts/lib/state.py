import contextlib
import hashlib
import json
import os
import re
import time
from pathlib import Path

SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9_.-]+")


def _hash(value):
    return hashlib.sha1(value.encode("utf-8")).hexdigest()[:12]


def _safe_name(value):
    return SAFE_NAME_RE.sub("_", value or "unknown-session")[:128]


def state_key(hook_input):
    session_id = hook_input.get("session_id")
    if session_id:
        return _safe_name(session_id)
    transcript_path = hook_input.get("transcript_path")
    if transcript_path:
        return f"transcript-{_hash(str(Path(transcript_path).expanduser().resolve()))}"
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

    def mark_submitted(self, session_key, fingerprints):
        with self.locked(session_key) as state:
            state.mark_submitted(fingerprints)

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
