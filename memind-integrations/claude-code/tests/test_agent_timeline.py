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
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from scripts.lib.agent_timeline import (
    build_timeline_payload,
    normalize_assistant_message_event,
    normalize_compact_boundary_event,
    normalize_hook_event,
    normalize_notification_event,
    normalize_session_end_event,
    normalize_stop_event,
    normalize_subagent_stop_event,
    normalize_user_prompt_event,
)


class AgentTimelineTest(unittest.TestCase):
    def test_normalizes_post_tool_use_to_test_result_event(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "npm test payment"},
                "tool_response": {"exit_code": 1, "stdout": "rounding mismatch"},
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
            turn_id="s-turn-1",
            turn_seq=1,
        )

        self.assertEqual(event["kind"], "test_result")
        self.assertIn("eventId", event)
        self.assertNotIn("id", event)
        self.assertEqual(event["seq"], 1)
        self.assertEqual(event["command"], "npm test payment")
        self.assertEqual(event["status"], "failed")
        self.assertEqual(event["exitCode"], 1)
        self.assertEqual(event["output"], '{"stdout": "rounding mismatch"}')
        self.assertEqual(event["metadata"]["validationType"], "test")
        self.assertEqual(event["metadata"]["normalizationVersion"], 1)
        self.assertEqual(event["metadata"]["sessionId"], "s")
        self.assertEqual(event["metadata"]["sourceClient"], "claude-code")
        self.assertEqual(event["metadata"]["turnId"], "s-turn-1")
        self.assertEqual(event["metadata"]["turnSeq"], 1)

    def test_normalizes_non_test_bash_to_command_event(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "git status --short"},
                "tool_response": {"exit_code": 0, "stdout": ""},
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
        )

        self.assertEqual(event["kind"], "command")
        self.assertEqual(event["command"], "git status --short")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["metadata"]["normalizationVersion"], 1)

    def test_normalizes_tool_telemetry_and_content_hash(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "npm test payment"},
                "tool_response": {
                    "exit_code": 0,
                    "stdout": "passed",
                    "duration_ms": 1234,
                    "usage": {"input_tokens": 11, "output_tokens": 22},
                },
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
        )

        self.assertEqual(event["durationMs"], 1234)
        self.assertEqual(event["inputTokens"], 11)
        self.assertEqual(event["outputTokens"], 22)
        self.assertTrue(event["contentHash"].startswith("sha256:"))
        self.assertEqual(len(event["contentHash"]), len("sha256:") + 64)
        self.assertEqual(event["output"], '{"stdout": "passed"}')
        self.assertEqual(event["metadata"]["normalizationVersion"], 1)

    def test_content_hash_uses_redacted_payload(self):
        first = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "CustomTool",
                "tool_input": {"token": "Bearer first-secret-value"},
                "tool_response": {"result": "ok"},
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
        )
        second = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "CustomTool",
                "tool_input": {"token": "Bearer second-secret-value"},
                "tool_response": {"result": "ok"},
                "timestamp": "2026-05-24T10:00:01Z",
            },
            seq=2,
        )

        self.assertEqual(first["contentHash"], second["contentHash"])
        self.assertIn("[REDACTED:bearer_token]", first["input"])
        self.assertIn("[REDACTED:bearer_token]", second["input"])

    def test_content_hash_ignores_volatile_telemetry_fields(self):
        first = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "npm test payment"},
                "tool_response": {
                    "exit_code": 0,
                    "stdout": "passed",
                    "duration_ms": 100,
                    "metadata": {"duration_ms": 100},
                    "usage": {"input_tokens": 11, "output_tokens": 22},
                },
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
        )
        second = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "npm test payment"},
                "tool_response": {
                    "exit_code": 0,
                    "stdout": "passed",
                    "duration_ms": 999,
                    "metadata": {"duration_ms": 999},
                    "usage": {"input_tokens": 100, "output_tokens": 200},
                },
                "timestamp": "2026-05-24T10:00:01Z",
            },
            seq=2,
        )

        self.assertEqual(first["contentHash"], second["contentHash"])
        self.assertNotIn("duration_ms", first.get("output", ""))
        self.assertNotIn("metadata", first.get("output", ""))
        self.assertNotIn("usage", first.get("output", ""))

    def test_normalizes_file_read_tool_with_path(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Read",
                "tool_input": {"file_path": "src/payment/calc.ts"},
                "tool_response": {"content": "export function calc() {}"},
                "timestamp": "2026-05-24T10:01:00Z",
            },
            seq=2,
        )

        self.assertEqual(event["kind"], "file_read")
        self.assertEqual(event["path"], "src/payment/calc.ts")
        self.assertEqual(event["operation"], "read")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["metadata"]["toolCategory"], "file")

    def test_normalizes_file_edit_tool_with_path_and_operation(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "MultiEdit",
                "tool_input": {"file_path": "src/payment/calc.ts", "edits": []},
                "tool_response": {"result": "ok"},
                "timestamp": "2026-05-24T10:02:00Z",
            },
            seq=3,
        )

        self.assertEqual(event["kind"], "file_edit")
        self.assertEqual(event["path"], "src/payment/calc.ts")
        self.assertEqual(event["operation"], "multi_edit")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["metadata"]["toolCategory"], "file")

    def test_preserves_search_tool_as_tool_result_with_search_metadata(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Grep",
                "tool_input": {"pattern": "AgentEpisodeAssembler", "path": "memind-plugins"},
                "tool_response": {"matches": ["AgentEpisodeAssembler.java"]},
                "timestamp": "2026-05-24T10:03:00Z",
            },
            seq=4,
        )

        self.assertEqual(event["kind"], "tool_result")
        self.assertEqual(event["path"], "memind-plugins")
        self.assertEqual(event["operation"], "search")
        self.assertEqual(event["metadata"]["toolCategory"], "search")
        self.assertEqual(event["metadata"]["searchPattern"], "AgentEpisodeAssembler")

    def test_normalizes_web_search_before_generic_search(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "WebSearch",
                "tool_input": {"query": "OpenMemind rawdata-agent"},
                "tool_response": {"results": []},
                "timestamp": "2026-05-24T10:03:30Z",
            },
            seq=5,
        )

        self.assertEqual(event["kind"], "tool_result")
        self.assertEqual(event["operation"], "web_search")
        self.assertEqual(event["metadata"]["toolCategory"], "web_search")
        self.assertEqual(event["metadata"]["query"], "OpenMemind rawdata-agent")

    def test_unknown_tool_keeps_raw_payload_and_extracts_path_when_available(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "CustomAnalyzer",
                "tool_input": {"target_file": "src/main/java/Foo.java", "mode": "deep"},
                "tool_response": {"summary": "ok"},
                "timestamp": "2026-05-24T10:04:00Z",
            },
            seq=5,
        )

        self.assertEqual(event["kind"], "tool_result")
        self.assertEqual(event["path"], "src/main/java/Foo.java")
        self.assertEqual(event["operation"], "unknown")
        self.assertIn('"mode": "deep"', event["input"])
        self.assertEqual(event["metadata"]["toolCategory"], "unknown")

    def test_unknown_tool_keeps_non_object_raw_input_and_output(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "CustomTool",
                "tool_input": "raw input text",
                "tool_response": "raw output text",
                "timestamp": "2026-05-24T10:05:00Z",
            },
            seq=6,
        )

        self.assertEqual(event["kind"], "tool_result")
        self.assertEqual(event["input"], "raw input text")
        self.assertEqual(event["output"], "raw output text")

    def test_normalizes_user_prompt_and_stop_events_with_turn_metadata(self):
        prompt_event = normalize_user_prompt_event(
            {
                "hook_event_name": "UserPromptSubmit",
                "session_id": "s",
                "prompt": "Fix payment tests",
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
            turn_id="s-turn-1",
            turn_seq=1,
        )
        stop_event = normalize_stop_event(
            {
                "hook_event_name": "Stop",
                "session_id": "s",
                "timestamp": "2026-05-24T10:04:00Z",
            },
            seq=2,
            turn_id="s-turn-1",
            turn_seq=1,
        )

        self.assertEqual(prompt_event["kind"], "user_prompt")
        self.assertEqual(prompt_event["text"], "Fix payment tests")
        self.assertEqual(prompt_event["metadata"]["sessionId"], "s")
        self.assertEqual(prompt_event["metadata"]["sourceClient"], "claude-code")
        self.assertEqual(prompt_event["metadata"]["turnId"], "s-turn-1")
        self.assertEqual(prompt_event["metadata"]["turnSeq"], 1)
        self.assertEqual(stop_event["kind"], "stop")
        self.assertEqual(stop_event["status"], "success")
        self.assertEqual(stop_event["metadata"]["sessionId"], "s")
        self.assertEqual(stop_event["metadata"]["sourceClient"], "claude-code")
        self.assertEqual(stop_event["metadata"]["turnId"], "s-turn-1")

    def test_normalizes_assistant_message_event_from_transcript_text(self):
        event = normalize_assistant_message_event(
            {
                "hook_event_name": "Stop",
                "session_id": "s",
                "timestamp": "2026-05-24T10:04:00Z",
            },
            seq=3,
            turn_id="s-turn-1",
            turn_seq=1,
            text="Updated calc.ts and tests now pass.",
        )

        self.assertEqual(event["kind"], "assistant_message")
        self.assertEqual(event["text"], "Updated calc.ts and tests now pass.")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["metadata"]["sessionId"], "s")
        self.assertEqual(event["metadata"]["sourceClient"], "claude-code")
        self.assertEqual(event["metadata"]["turnId"], "s-turn-1")

    def test_normalizes_notification_event(self):
        event = normalize_notification_event(
            {
                "hook_event_name": "Notification",
                "session_id": "s",
                "message": "Claude needs permission to run Bash",
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
            turn_id="s-turn-1",
            turn_seq=1,
        )

        self.assertEqual(event["kind"], "notification")
        self.assertEqual(event["text"], "Claude needs permission to run Bash")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["metadata"]["notificationKind"], "blocked")
        self.assertEqual(event["metadata"]["failureSignal"], "Claude needs permission to run Bash")

    def test_normalizes_subagent_stop_event(self):
        event = normalize_subagent_stop_event(
            {
                "hook_event_name": "SubagentStop",
                "session_id": "s",
                "subagent_type": "explorer",
                "message": "Found failing resolver test",
                "timestamp": "2026-05-24T10:01:00Z",
            },
            seq=2,
            turn_id="s-turn-1",
            turn_seq=1,
        )

        self.assertEqual(event["kind"], "subagent_stop")
        self.assertEqual(event["operation"], "explorer")
        self.assertIn("Found failing resolver test", event["text"])
        self.assertEqual(event["metadata"]["subagentType"], "explorer")

    def test_normalizes_compact_boundary_event(self):
        event = normalize_compact_boundary_event(
            {
                "hook_event_name": "PreCompact",
                "session_id": "s",
                "timestamp": "2026-05-24T10:02:00Z",
            },
            seq=3,
            turn_id="s-turn-1",
            turn_seq=1,
        )

        self.assertEqual(event["kind"], "compact_boundary")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["operation"], "compact")

    def test_normalizes_session_end_event(self):
        event = normalize_session_end_event(
            {
                "hook_event_name": "SessionEnd",
                "session_id": "s",
                "timestamp": "2026-05-24T10:03:00Z",
            },
            seq=4,
            turn_id="s-turn-1",
            turn_seq=1,
        )

        self.assertEqual(event["kind"], "session_end")
        self.assertEqual(event["status"], "success")
        self.assertEqual(event["operation"], "session_end")

    def test_redacts_secret_fields_before_spool(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "echo sk-test-secret"},
                "tool_response": {
                    "exit_code": 0,
                    "stdout": "Authorization: Bearer abc.def.ghi\nsk-live-secret",
                },
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
        )

        serialized = json.dumps(event)
        self.assertNotIn("sk-test-secret", serialized)
        self.assertNotIn("sk-live-secret", serialized)
        self.assertNotIn("abc.def.ghi", serialized)
        self.assertIn("[REDACTED", serialized)

    def test_builds_agent_timeline_payload(self):
        event = normalize_hook_event(
            {
                "hook_event_name": "PostToolUse",
                "session_id": "s",
                "tool_name": "Bash",
                "tool_input": {"command": "git status --short"},
                "tool_response": {"exit_code": 0},
                "timestamp": "2026-05-24T10:00:00Z",
            },
            seq=1,
            turn_id="s-turn-2",
            turn_seq=2,
        )

        payload = build_timeline_payload(
            config={"sourceClient": "claude-code"},
            identity={"userId": "u", "agentId": "a"},
            session_id="s",
            events=[event],
            hook_input={"cwd": "/tmp/project"},
        )

        self.assertEqual(payload["type"], "agent_timeline")
        self.assertEqual(payload["sourceClient"], "claude-code")
        self.assertEqual(payload["sessionId"], "s")
        self.assertEqual(payload["agentTurnId"], "s-turn-2")
        self.assertEqual(payload["timelineId"], "s-turn-2-timeline")
        self.assertEqual(payload["metadata"]["sessionId"], "s")
        self.assertEqual(payload["metadata"]["sourceClient"], "claude-code")
        self.assertEqual(payload["metadata"]["turnId"], "s-turn-2")
        self.assertEqual(payload["metadata"]["turnSeq"], 2)
        self.assertEqual(payload["metadata"]["eventIds"], [event["eventId"]])
        self.assertIn("eventId", payload["events"][0])
        self.assertEqual(payload["events"][0]["seq"], 1)
        self.assertEqual(payload["project"]["name"], "project")
        self.assertEqual(payload["project"]["rootPath"], "/tmp/project")
        project_slug = payload["project"]["metadata"]["projectSlug"]
        self.assertRegex(project_slug, r"^project-[a-f0-9]{12}$")
        self.assertEqual(payload["metadata"]["projectSlug"], project_slug)


if __name__ == "__main__":
    unittest.main()
