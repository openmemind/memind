import json
import os
from pathlib import Path

DEFAULT_SETTINGS = {
    "memindApiUrl": "http://127.0.0.1:8366",
    "memindApiToken": None,
    "userId": None,
    "agentId": "codex",
    "agentIdMode": "project",
    "sourceClient": "codex",
    "autoRetrieve": True,
    "autoIngest": True,
    "commitOnStop": False,
    "retrieveStrategy": "SIMPLE",
    "retrieveMaxEntries": 8,
    "retrieveMaxChars": 6000,
    "retrievePromptPreamble": "Relevant memories from Memind. Use only when directly helpful:",
    "retrieveContextTurns": 0,
    "ingestionMode": "add-message",
    "ingestionRoles": ["user", "assistant"],
    "ingestionMaxMessagesPerHook": 20,
    "stateMaxAgeDays": 14,
    "ingestRetrySpool": True,
    "ingestRetryMaxFiles": 20,
    "ingestRetryMaxAgeDays": 7,
    "debug": False,
}

ENV_MAP = {
    "MEMIND_API_URL": ("memindApiUrl", str),
    "MEMIND_API_TOKEN": ("memindApiToken", str),
    "MEMIND_USER_ID": ("userId", str),
    "MEMIND_AGENT_ID": ("agentId", str),
    "MEMIND_AGENT_ID_MODE": ("agentIdMode", str),
    "MEMIND_SOURCE_CLIENT": ("sourceClient", str),
    "MEMIND_AUTO_RETRIEVE": ("autoRetrieve", "bool"),
    "MEMIND_AUTO_INGEST": ("autoIngest", "bool"),
    "MEMIND_COMMIT_ON_STOP": ("commitOnStop", "bool"),
    "MEMIND_RETRIEVE_STRATEGY": ("retrieveStrategy", str),
    "MEMIND_RETRIEVE_MAX_ENTRIES": ("retrieveMaxEntries", "int"),
    "MEMIND_RETRIEVE_MAX_CHARS": ("retrieveMaxChars", "int"),
    "MEMIND_RETRIEVE_CONTEXT_TURNS": ("retrieveContextTurns", "int_allow_zero"),
    "MEMIND_INGESTION_MODE": ("ingestionMode", str),
    "MEMIND_INGESTION_ROLES": ("ingestionRoles", "list"),
    "MEMIND_INGESTION_MAX_MESSAGES_PER_HOOK": ("ingestionMaxMessagesPerHook", "int"),
    "MEMIND_STATE_MAX_AGE_DAYS": ("stateMaxAgeDays", "int"),
    "MEMIND_INGEST_RETRY_SPOOL": ("ingestRetrySpool", "bool"),
    "MEMIND_INGEST_RETRY_MAX_FILES": ("ingestRetryMaxFiles", "int"),
    "MEMIND_INGEST_RETRY_MAX_AGE_DAYS": ("ingestRetryMaxAgeDays", "int"),
    "MEMIND_DEBUG": ("debug", "bool"),
}


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


def load_config(plugin_root=None, user_config_path=None, env=None):
    env = os.environ if env is None else env
    root = Path(plugin_root or env.get("CODEX_PLUGIN_ROOT") or Path(__file__).resolve().parents[2])
    config = dict(DEFAULT_SETTINGS)
    settings_path = root / "settings.json"
    if settings_path.exists():
        config.update(json.loads(settings_path.read_text()))
    user_path = Path(user_config_path or env.get("MEMIND_CODEX_CONFIG") or Path.home() / ".memind" / "codex.json")
    if user_path.exists():
        config.update(json.loads(user_path.read_text()))
    for env_name, (key, kind) in ENV_MAP.items():
        if env_name in env and env[env_name] != "":
            config[key] = _coerce(env[env_name], kind, key)
    return config
