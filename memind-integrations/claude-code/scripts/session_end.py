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
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from ingest import ingest_messages
from lib.config import load_config
from lib.logging_utils import debug_log


def main():
    try:
        hook_input = json.loads(sys.stdin.read() or "{}")
        config = load_config()
        ingest_messages(config, hook_input, commit=bool(config.get("commitOnSessionEnd", True)))
    except Exception as exc:
        try:
            debug_log(load_config(), "session_end_failed", {"error": str(exc)})
        except Exception:
            pass
    print(json.dumps({"continue": True}))


if __name__ == "__main__":
    main()
