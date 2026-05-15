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
import threading
from urllib.parse import urlparse

from memind_hermes.client import MemindClient, build_conversation_content
from memind_hermes.config import config_schema, load_config, save_config as save_provider_config
from memind_hermes.content import text_or_empty
from memind_hermes.format import format_memind_context
from memind_hermes.identity import resolve_identity

try:
    from agent.memory_provider import MemoryProvider
except ImportError:
    class MemoryProvider:
        pass


VALID_TOOL_STRATEGIES = {"SIMPLE", "DEEP"}


def _valid_url(value):
    parsed = urlparse(value or "")
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


class MemindMemoryProvider(MemoryProvider):
    def __init__(self, config_loader=None, client_factory=None):
        self._config_loader = config_loader or load_config
        self._client_factory_injected = client_factory is not None
        self._client_factory = client_factory or self._default_client
        self._config = None
        self._client = None
        self._identity = None
        self._session_id = None
        self._cwd = None
        self._prefetch_thread = None
        self._prefetch_result = ""
        self._sync_thread = None

    @property
    def name(self):
        return "memind"

    def _default_client(self, config):
        return MemindClient(
            config["memindApiUrl"],
            config.get("memindApiToken"),
            timeout=10,
            max_retries=0,
        )

    def _ensure_initialized(self):
        if self._config is None:
            self.initialize("unknown-session", cwd=os.getcwd())

    def _debug(self, message):
        if self._config and self._config.get("debug"):
            print(f"memind-hermes: {message}")

    def is_available(self):
        config = self._config_loader()
        if not _valid_url(config.get("memindApiUrl")):
            return False
        try:
            import memind  # noqa: F401
        except ImportError:
            return self._client_factory_injected
        return True

    def initialize(self, session_id, **kwargs):
        self._config = self._config_loader()
        self._session_id = session_id
        self._cwd = kwargs.get("cwd") or os.getcwd()
        self._identity = resolve_identity(self._config, cwd=self._cwd, session_id=session_id)
        self._client = self._client_factory(self._config)

    def get_config_schema(self):
        return config_schema()

    def save_config(self, values, hermes_home):
        save_provider_config(values)

    def system_prompt_block(self):
        return ""

    def _auto_retrieve_enabled(self):
        return self._config.get("autoRetrieve", True) and self._config.get("memoryMode", "hybrid") in {"hybrid", "context"}

    def _tools_enabled(self):
        return self._config.get("memoryMode", "hybrid") in {"hybrid", "tools"}

    def prefetch(self, query, **kwargs):
        self._ensure_initialized()
        if not self._auto_retrieve_enabled():
            return ""
        cleaned = text_or_empty(query)
        if len(cleaned) < 5:
            return ""
        try:
            result = self._client.retrieve(
                self._identity["userId"],
                self._identity["agentId"],
                cleaned,
                self._config.get("retrieveStrategy", "SIMPLE"),
                False,
            )
            return format_memind_context(result, self._config)
        except Exception as exc:
            self._debug(f"prefetch failed: {exc}")
            return ""

    def queue_prefetch(self, query, **kwargs):
        def run():
            self._prefetch_result = self.prefetch(query, **kwargs)

        self._prefetch_thread = threading.Thread(target=run, daemon=True)
        self._prefetch_thread.start()

    def _extract_pairs(self, pairs):
        allowed_roles = {str(role).upper() for role in self._config.get("ingestionRoles", ["user", "assistant"])}
        filtered = [
            (role, text_or_empty(text))
            for role, text in pairs
            if str(role).upper() in allowed_roles
        ]
        filtered = [(role, text) for role, text in filtered if text]
        if not filtered:
            return
        raw_content = build_conversation_content(filtered)
        self._client.extract(
            self._identity["userId"],
            self._identity["agentId"],
            raw_content,
            self._identity["sourceClient"],
        )

    def sync_turn(self, user=None, assistant=None, **kwargs):
        self._ensure_initialized()
        if not self._config.get("autoIngest", True):
            return
        user_text = kwargs.get("user_content", user)
        assistant_text = kwargs.get("assistant_content", assistant)

        def run():
            try:
                self._extract_pairs([("USER", user_text), ("ASSISTANT", assistant_text)])
            except Exception as exc:
                self._debug(f"sync turn failed: {exc}")

        self._sync_thread = threading.Thread(target=run, daemon=True)
        self._sync_thread.start()

    def on_session_end(self, messages, **kwargs):
        return None

    def on_pre_compress(self, messages, **kwargs):
        self._ensure_initialized()
        if not self._auto_retrieve_enabled():
            return
        try:
            if any(
                isinstance(message, dict)
                and (
                    "<memind_memories>" in str(message.get("content", ""))
                    or "[Memind context before compaction]" in str(message.get("content", ""))
                )
                for message in messages
            ):
                return
            context_turns = int(self._config.get("retrieveContextTurns", 1))
            recent = messages[-context_turns * 2 :] if context_turns > 0 else messages[-1:]
            query = "\n".join(str(message.get("content", "")) for message in recent if isinstance(message, dict))
            context = self.prefetch(query or kwargs.get("query", "session context"))
            if context:
                messages.insert(
                    0,
                    {"role": "user", "content": f"[Memind context before compaction]\n{context}"},
                )
        except Exception as exc:
            self._debug(f"pre-compress failed: {exc}")

    def on_memory_write(self, action, target, content, **kwargs):
        self._ensure_initialized()
        if action not in {"add", "update"} or not text_or_empty(content):
            return

        def run():
            try:
                self._extract_pairs([("USER", content)])
            except Exception as exc:
                self._debug(f"memory write failed: {exc}")

        self._sync_thread = threading.Thread(target=run, daemon=True)
        self._sync_thread.start()

    def get_tool_schemas(self):
        self._ensure_initialized()
        if not self._tools_enabled():
            return []
        return [
            {
                "name": "memind_retrieve",
                "description": "Retrieve relevant Memind memories for the current user and agent scope.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "Natural-language retrieval query."},
                        "strategy": {
                            "type": "string",
                            "enum": ["SIMPLE", "DEEP"],
                            "default": "SIMPLE",
                            "description": "Memind retrieval strategy.",
                        },
                    },
                    "required": ["query"],
                },
            },
            {
                "name": "memind_extract_text",
                "description": (
                    "Extract memory from standalone text such as pasted notes or document excerpts. "
                    "Normal conversation turns are captured automatically by the Memind Hermes provider."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "text": {"type": "string", "description": "Standalone text to extract into memory."}
                    },
                    "required": ["text"],
                },
            },
        ]

    def handle_tool_call(self, name, args):
        self._ensure_initialized()
        try:
            if name == "memind_retrieve":
                strategy = str(args.get("strategy") or "SIMPLE").upper()
                if strategy not in VALID_TOOL_STRATEGIES:
                    return json.dumps({"error": "strategy must be SIMPLE or DEEP"})
                result = self._client.retrieve(
                    self._identity["userId"],
                    self._identity["agentId"],
                    args.get("query", ""),
                    strategy,
                    False,
                )
                payload = result.model_dump(by_alias=True) if hasattr(result, "model_dump") else result
                return json.dumps(payload or {}, default=str)
            if name == "memind_extract_text":
                text = text_or_empty(args.get("text"))
                if not text:
                    return json.dumps({"error": "text must not be blank"})
                raw_content = build_conversation_content([("USER", text)])
                result = self._client.extract(
                    self._identity["userId"],
                    self._identity["agentId"],
                    raw_content,
                    self._identity["sourceClient"],
                )
                payload = result.model_dump(by_alias=True) if hasattr(result, "model_dump") else {"status": getattr(result, "status", "SUCCESS")}
                return json.dumps(payload, default=str)
            return json.dumps({"error": f"Unknown tool: {name}"})
        except Exception as exc:
            self._debug(f"tool call failed: {exc}")
            return json.dumps({"error": str(exc)})

    def shutdown(self, **kwargs):
        return None


def register(ctx):
    ctx.register_memory_provider(MemindMemoryProvider())
