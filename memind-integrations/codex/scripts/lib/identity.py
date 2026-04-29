import getpass
import hashlib
import os
import subprocess
from pathlib import Path

SEPARATOR = "__"


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


def project_slug(cwd):
    path = Path(cwd or os.getcwd()).resolve()
    name = path.name or "project"
    remote = _git_remote(path)
    if remote:
        return f"{name}-{_hash(remote)}"
    return f"{name}-{_hash(str(path))}"


def resolve_identity(config, hook_input):
    cwd = hook_input.get("cwd") or os.getcwd()
    user_id = config.get("userId") or f"local{SEPARATOR}{getpass.getuser()}"
    base_agent = config.get("agentId") or "codex"
    if config.get("agentIdMode") == "project":
        agent_id = f"{base_agent}{SEPARATOR}{project_slug(cwd)}"
    else:
        agent_id = base_agent
    return {"userId": user_id, "agentId": agent_id}
