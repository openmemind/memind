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
    "agentId": "coding-agent",
    "sourceClient": "claude-code",
    "autoRetrieve": True,
    "autoPromptContext": False,
    "autoSessionContext": True,
    "autoIngestAgentTimeline": True,
    "retrieveStrategy": "SIMPLE",
    "retrieveMaxEntries": 8,
    "retrieveMaxChars": 6000,
    "retrievePromptPreamble": "Relevant memories from Memind. Use only when directly helpful:",
    "retrieveContextTurns": 0,
    "promptContextProjectMinEntries": 4,
    "promptContextGlobalFallbackEntries": 3,
    "promptContextGlobalFallbackMinScore": 0.65,
    "autoToolContext": True,
    "toolContextMaxChars": 3500,
    "toolContextEntryMaxChars": 520,
    "toolContextMaxItems": 6,
    "toolContextMinExactItems": 2,
    "sessionContextRecentSessions": 3,
    "sessionContextMaxItems": 6,
    "sessionContextMaxChars": 6000,
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
    "MEMIND_SOURCE_CLIENT": ("sourceClient", str),
    "MEMIND_AUTO_RETRIEVE": ("autoRetrieve", "bool"),
    "MEMIND_AUTO_PROMPT_CONTEXT": ("autoPromptContext", "bool"),
    "MEMIND_AUTO_SESSION_CONTEXT": ("autoSessionContext", "bool"),
    "MEMIND_AUTO_INGEST_AGENT_TIMELINE": ("autoIngestAgentTimeline", "bool"),
    "MEMIND_RETRIEVE_STRATEGY": ("retrieveStrategy", str),
    "MEMIND_RETRIEVE_MAX_ENTRIES": ("retrieveMaxEntries", "int"),
    "MEMIND_RETRIEVE_MAX_CHARS": ("retrieveMaxChars", "int"),
    "MEMIND_RETRIEVE_CONTEXT_TURNS": ("retrieveContextTurns", "int_allow_zero"),
    "MEMIND_PROMPT_CONTEXT_PROJECT_MIN_ENTRIES": ("promptContextProjectMinEntries", "int_allow_zero"),
    "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_ENTRIES": ("promptContextGlobalFallbackEntries", "int_allow_zero"),
    "MEMIND_PROMPT_CONTEXT_GLOBAL_FALLBACK_MIN_SCORE": ("promptContextGlobalFallbackMinScore", "float_allow_zero"),
    "MEMIND_AUTO_TOOL_CONTEXT": ("autoToolContext", "bool"),
    "MEMIND_TOOL_CONTEXT_MAX_CHARS": ("toolContextMaxChars", "int"),
    "MEMIND_TOOL_CONTEXT_ENTRY_MAX_CHARS": ("toolContextEntryMaxChars", "int"),
    "MEMIND_TOOL_CONTEXT_MAX_ITEMS": ("toolContextMaxItems", "int"),
    "MEMIND_TOOL_CONTEXT_MIN_EXACT_ITEMS": ("toolContextMinExactItems", "int_allow_zero"),
    "MEMIND_SESSION_CONTEXT_RECENT_SESSIONS": ("sessionContextRecentSessions", "int"),
    "MEMIND_SESSION_CONTEXT_MAX_ITEMS": ("sessionContextMaxItems", "int"),
    "MEMIND_SESSION_CONTEXT_MAX_CHARS": ("sessionContextMaxChars", "int"),
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


def parse_float(value, name, allow_zero=False):
    parsed = float(value)
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
    if kind == "float_allow_zero":
        return parse_float(value, name, allow_zero=True)
    if kind == "list":
        return parse_list(value)
    return kind(value)


def load_config(plugin_root=None, user_config_path=None, env=None):
    env = os.environ if env is None else env
    root = Path(plugin_root or env.get("CLAUDE_PLUGIN_ROOT") or Path(__file__).resolve().parents[2])
    config = dict(DEFAULT_SETTINGS)
    settings_path = root / "settings.json"
    if settings_path.exists():
        config.update(json.loads(settings_path.read_text()))
    user_path = Path(user_config_path or env.get("MEMIND_CLAUDE_CONFIG") or Path.home() / ".memind" / "claude-code.json")
    if user_path.exists():
        config.update(json.loads(user_path.read_text()))
    for env_name, (key, kind) in ENV_MAP.items():
        if env_name in env and env[env_name] != "":
            config[key] = _coerce(env[env_name], kind, key)
    return config
