import json
import time
from pathlib import Path

LOG_PATH = Path.home() / ".memind" / "codex.log"


def debug_log(config, event, details=None):
    if not config.get("debug"):
        return
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    safe_details = dict(details or {})
    for key in list(safe_details):
        if "transcript" in key.lower() or "payload" in key.lower():
            safe_details[key] = "<redacted>"
    entry = {"timestamp": time.time(), "event": event, "details": safe_details}
    with LOG_PATH.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(entry, ensure_ascii=False, sort_keys=True) + "\n")
