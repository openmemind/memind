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

import getpass
import hashlib
import os
import re
import subprocess
from pathlib import Path


SEPARATOR = "__"
SAFE_PART_RE = re.compile(r"[^A-Za-z0-9_.-]+")


def _hash(value):
    return hashlib.sha1(value.encode("utf-8")).hexdigest()[:12]


def _git_remote(cwd):
    try:
        result = subprocess.run(
            ["git", "-C", str(cwd), "config", "--get", "remote.origin.url"],
            capture_output=True,
            check=False,
            text=True,
            timeout=1,
        )
    except Exception:
        return None
    remote = result.stdout.strip()
    return remote or None


def _safe_part(value):
    cleaned = SAFE_PART_RE.sub("_", str(value or "").strip())
    return cleaned[:128] or "unknown"


def project_slug(cwd):
    path = Path(cwd or os.getcwd()).resolve()
    name = _safe_part(path.name or "project")
    remote = _git_remote(path)
    if remote:
        return f"{name}-{_hash(remote)}"
    return f"{name}-{_hash(str(path))}"


def resolve_identity(config, cwd=None, session_id=None):
    user_id = config.get("userId") or f"local{SEPARATOR}{getpass.getuser()}"
    base_agent = config.get("agentId") or "hermes"
    mode = config.get("agentIdMode") or "project"
    if mode == "fixed":
        agent_id = base_agent
    elif mode == "session":
        agent_id = f"{base_agent}{SEPARATOR}session-{_safe_part(session_id or cwd or 'default-session')}"
    else:
        agent_id = f"{base_agent}{SEPARATOR}{project_slug(cwd)}"
    return {
        "userId": user_id,
        "agentId": agent_id,
        "sourceClient": config.get("sourceClient") or "hermes",
    }
