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
from pathlib import Path


DEFAULT_SETTINGS = {
    "memindApiUrl": "http://127.0.0.1:8366",
    "memindApiToken": None,
    "userId": None,
    "agentId": "hermes",
    "agentIdMode": "project",
    "sourceClient": "hermes",
    "memoryMode": "hybrid",
    "autoRetrieve": True,
    "autoIngest": True,
    "retrieveStrategy": "SIMPLE",
    "retrieveMaxEntries": 8,
    "retrieveMaxChars": 6000,
    "retrieveContextTurns": 1,
    "retrievePromptPreamble": "Relevant memories from Memind. Use only when directly helpful:",
    "ingestionRoles": ["user", "assistant"],
    "debug": False,
}

ENV_MAP = {
    "MEMIND_API_URL": ("memindApiUrl", str),
    "MEMIND_API_TOKEN": ("memindApiToken", str),
    "MEMIND_USER_ID": ("userId", str),
    "MEMIND_AGENT_ID": ("agentId", str),
    "MEMIND_AGENT_ID_MODE": ("agentIdMode", str),
    "MEMIND_SOURCE_CLIENT": ("sourceClient", str),
    "MEMIND_MEMORY_MODE": ("memoryMode", str),
    "MEMIND_AUTO_RETRIEVE": ("autoRetrieve", "bool"),
    "MEMIND_AUTO_INGEST": ("autoIngest", "bool"),
    "MEMIND_RETRIEVE_STRATEGY": ("retrieveStrategy", str),
    "MEMIND_RETRIEVE_MAX_ENTRIES": ("retrieveMaxEntries", "int"),
    "MEMIND_RETRIEVE_MAX_CHARS": ("retrieveMaxChars", "int"),
    "MEMIND_RETRIEVE_CONTEXT_TURNS": ("retrieveContextTurns", "int_allow_zero"),
    "MEMIND_INGESTION_ROLES": ("ingestionRoles", "list"),
    "MEMIND_DEBUG": ("debug", "bool"),
}

VALID_MEMORY_MODES = {"hybrid", "context", "tools"}
VALID_RETRIEVE_STRATEGIES = {"SIMPLE", "DEEP"}


def parse_bool(value):
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def parse_int(value, name, allow_zero=False):
    parsed = int(value)
    if parsed < 0 or (parsed == 0 and not allow_zero):
        raise ValueError(f"{name} must be positive")
    return parsed


def parse_list(value):
    return [part.strip() for part in str(value).split(",") if part.strip()]


def _coerce(value, kind, name):
    if kind == "bool":
        return parse_bool(value)
    if kind == "int":
        return parse_int(value, name)
    if kind == "int_allow_zero":
        return parse_int(value, name, allow_zero=True)
    if kind == "list":
        return parse_list(value)
    return kind(value)


def _default_user_config_path(home=None):
    base = Path(home) if home else Path.home()
    return base / ".memind" / "hermes.json"


def _normalize(config):
    mode = str(config.get("memoryMode", "hybrid")).strip().lower()
    config["memoryMode"] = mode if mode in VALID_MEMORY_MODES else "hybrid"
    strategy = str(config.get("retrieveStrategy", "SIMPLE")).strip().upper()
    config["retrieveStrategy"] = strategy if strategy in VALID_RETRIEVE_STRATEGIES else "SIMPLE"
    config["memindApiUrl"] = str(config.get("memindApiUrl", DEFAULT_SETTINGS["memindApiUrl"])).rstrip("/")
    return config


def load_config(plugin_root=None, user_config_path=None, env=None):
    env = os.environ if env is None else env
    root = Path(plugin_root or Path(__file__).resolve().parents[1])
    config = dict(DEFAULT_SETTINGS)
    settings_path = root / "settings.json"
    if settings_path.exists():
        config.update(json.loads(settings_path.read_text()))
    user_path = Path(user_config_path or env.get("MEMIND_HERMES_CONFIG") or _default_user_config_path())
    if user_path.exists():
        config.update(json.loads(user_path.read_text()))
    for env_name, (key, kind) in ENV_MAP.items():
        if env_name in env and env[env_name] != "":
            config[key] = _coerce(env[env_name], kind, key)
    return _normalize(config)


def save_config(values, home=None):
    path = _default_user_config_path(home)
    path.parent.mkdir(parents=True, exist_ok=True)
    normalized = _normalize({**DEFAULT_SETTINGS, **dict(values)})
    path.write_text(json.dumps(normalized, indent=2) + "\n")
    return path


def config_schema():
    return [
        {
            "key": "memindApiUrl",
            "description": "Memind server URL",
            "default": DEFAULT_SETTINGS["memindApiUrl"],
            "env_var": "MEMIND_API_URL",
        },
        {
            "key": "memindApiToken",
            "description": "Memind API token",
            "secret": True,
            "required": False,
            "env_var": "MEMIND_API_TOKEN",
        },
        {
            "key": "userId",
            "description": "Memind user identity. Defaults to local__<system-user>.",
            "required": False,
            "env_var": "MEMIND_USER_ID",
        },
        {
            "key": "agentId",
            "description": "Base Memind agent identity",
            "default": DEFAULT_SETTINGS["agentId"],
            "env_var": "MEMIND_AGENT_ID",
        },
        {
            "key": "memoryMode",
            "description": "Memory mode: hybrid, context, or tools",
            "default": DEFAULT_SETTINGS["memoryMode"],
            "env_var": "MEMIND_MEMORY_MODE",
        },
    ]
