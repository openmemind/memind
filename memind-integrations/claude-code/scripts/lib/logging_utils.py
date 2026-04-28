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
from pathlib import Path

LOG_PATH = Path.home() / ".memind" / "claude-code.log"


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
