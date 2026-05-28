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

from memind_hermes.client import build_agent_timeline_content, build_conversation_content


class ClientTest(unittest.TestCase):
    def test_build_conversation_content_uses_official_models_when_available(self):
        content = build_conversation_content([("USER", "hello"), ("ASSISTANT", "hi")])

        dumped = content.model_dump(by_alias=True, exclude_none=True)
        self.assertEqual(dumped["type"], "conversation")
        self.assertEqual(dumped["messages"][0]["role"], "USER")
        self.assertEqual(dumped["messages"][0]["content"][0]["text"], "hello")

    def test_build_conversation_content_rejects_empty_messages(self):
        with self.assertRaises(ValueError):
            build_conversation_content([])

    def test_build_agent_timeline_content_uses_map_raw_content(self):
        content = build_agent_timeline_content(
            {
                "sourceClient": "hermes",
                "sessionId": "s1",
                "agentTurnId": "turn-1",
                "timelineId": "timeline-1",
                "events": [{"eventId": "e1", "seq": 1, "kind": "user_prompt"}],
                "metadata": {"profile": "general", "runtime": "hermes"},
            }
        )

        dumped = content.model_dump(by_alias=True, exclude_none=True)
        self.assertEqual(dumped["type"], "agent_timeline")
        self.assertEqual(dumped["events"][0]["kind"], "user_prompt")


if __name__ == "__main__":
    unittest.main()
