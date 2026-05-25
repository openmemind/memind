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
import unittest

from scripts.lib.agent_timeline import (
    build_timeline_payload,
    normalize_assistant_message_event,
    normalize_hook_event,
    normalize_stop_event,
    normalize_user_prompt_event,
)


class AgentTimelineTest(unittest.TestCase):
    def test_normalizes_post_tool_use_to_command_event(self):
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

        self.assertEqual(event["kind"], "command")
        self.assertIn("eventId", event)
        self.assertNotIn("id", event)
        self.assertEqual(event["seq"], 1)
        self.assertEqual(event["command"], "npm test payment")
        self.assertEqual(event["status"], "failed")
        self.assertEqual(event["exitCode"], 1)
        self.assertEqual(event["output"], '{"stdout": "rounding mismatch"}')
        self.assertEqual(event["metadata"]["turnId"], "s-turn-1")
        self.assertEqual(event["metadata"]["turnSeq"], 1)

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
        self.assertEqual(prompt_event["metadata"]["turnId"], "s-turn-1")
        self.assertEqual(prompt_event["metadata"]["turnSeq"], 1)
        self.assertEqual(stop_event["kind"], "stop")
        self.assertEqual(stop_event["status"], "success")
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
        self.assertEqual(event["metadata"]["turnId"], "s-turn-1")

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
                "tool_input": {"command": "npm test payment"},
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
        self.assertEqual(payload["metadata"]["turnId"], "s-turn-2")
        self.assertEqual(payload["metadata"]["turnSeq"], 2)
        self.assertIn("eventId", payload["events"][0])
        self.assertEqual(payload["events"][0]["seq"], 1)
        self.assertEqual(payload["project"]["name"], "project")
        self.assertEqual(payload["project"]["rootPath"], "/tmp/project")


if __name__ == "__main__":
    unittest.main()
