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

import unittest

from memind_hermes.agent_timeline import (
    assistant_message_event,
    build_timeline_content,
    compact_boundary_event,
    memory_write_event,
    session_end_event,
    stop_event,
    user_prompt_event,
)


class AgentTimelineTest(unittest.TestCase):
    def test_user_prompt_event_strips_memind_context(self):
        event = user_prompt_event(
            "<memind_memories>x</memind_memories>hello",
            1,
            session_id="s1",
            metadata={"turnId": "turn-1"},
        )

        self.assertEqual(event["kind"], "user_prompt")
        self.assertEqual(event["text"], "hello")
        self.assertEqual(event["metadata"]["turnId"], "turn-1")
        self.assertTrue(event["eventId"].startswith("hermes-"))

    def test_assistant_message_event(self):
        event = assistant_message_event("hi", 2, session_id="s1")

        self.assertEqual(event["kind"], "assistant_message")
        self.assertEqual(event["text"], "hi")

    def test_memory_write_event(self):
        event = memory_write_event("add", "MEMORY.md", "remember espresso", 3, session_id="s1")

        self.assertEqual(event["kind"], "notification")
        self.assertEqual(event["text"], "remember espresso")
        self.assertEqual(event["metadata"]["memoryWrite"], True)
        self.assertEqual(event["metadata"]["target"], "MEMORY.md")

    def test_compact_stop_and_session_end_events(self):
        self.assertEqual(compact_boundary_event([], 4, session_id="s1")["kind"], "compact_boundary")
        self.assertEqual(stop_event(5, session_id="s1", success=True)["status"], "success")
        self.assertEqual(session_end_event(6, session_id="s1")["kind"], "session_end")

    def test_build_timeline_content_sets_general_metadata(self):
        events = [
            user_prompt_event("hello", 1, session_id="s1"),
            stop_event(2, session_id="s1", success=True),
        ]
        content = build_timeline_content(
            source_client="hermes",
            session_id="s1",
            agent_turn_id="turn-1",
            timeline_id="timeline-1",
            events=events,
            metadata={"workspaceDir": "/tmp/acme"},
        )

        self.assertEqual(content["type"], "agent_timeline")
        self.assertEqual(content["metadata"]["profile"], "general")
        self.assertEqual(content["metadata"]["runtime"], "hermes")
        self.assertEqual(content["metadata"]["sessionKey"], "s1")


if __name__ == "__main__":
    unittest.main()
