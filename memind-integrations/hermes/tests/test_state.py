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

import tempfile
import unittest
from pathlib import Path

from memind_hermes.state import AgentTimelineState


class StateTest(unittest.TestCase):
    def test_appends_events_in_order_and_ignores_duplicates(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = AgentTimelineState(Path(tmp), max_events=500)
            state.append("s1", {"eventId": "e2", "seq": 2, "kind": "assistant_message"})
            state.append("s1", {"eventId": "e1", "seq": 1, "kind": "user_prompt"})
            state.append("s1", {"eventId": "e1", "seq": 1, "kind": "user_prompt", "text": "dup"})

            self.assertEqual([event["eventId"] for event in state.list("s1")], ["e1", "e2"])

    def test_soft_cap_keeps_latest_events(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = AgentTimelineState(Path(tmp), max_events=2)
            state.append("s1", {"eventId": "e1", "seq": 1, "kind": "user_prompt"})
            state.append("s1", {"eventId": "e2", "seq": 2, "kind": "assistant_message"})
            state.append("s1", {"eventId": "e3", "seq": 3, "kind": "stop"})

            self.assertEqual([event["eventId"] for event in state.list("s1")], ["e2", "e3"])

    def test_clear_and_next_seq(self):
        with tempfile.TemporaryDirectory() as tmp:
            state = AgentTimelineState(Path(tmp), max_events=500)
            state.append("s1", {"eventId": "e1", "seq": 1, "kind": "user_prompt"})
            state.append("s1", {"eventId": "e3", "seq": 3, "kind": "stop"})

            self.assertEqual(state.next_seq("s1"), 4)
            state.clear("s1", ["e1"])
            self.assertEqual([event["eventId"] for event in state.list("s1")], ["e3"])
            state.clear("s1")
            self.assertEqual(state.list("s1"), [])


if __name__ == "__main__":
    unittest.main()
