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

import sys
import types
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from scripts.lib.session_context import build_session_context, render_session_context


class FakeClient:
    def __init__(self):
        self.raw_data_calls = []
        self.item_calls = []

    def query_raw_data(self, user_id, agent_id, **kwargs):
        self.raw_data_calls.append((user_id, agent_id, kwargs))
        return types.SimpleNamespace(
            raw_data=[
                types.SimpleNamespace(
                    id="rd-1",
                    caption="Implemented generic memory query APIs and all clients.",
                    metadata={"projectSlug": "memind-main"},
                    created_at="2026-05-27T01:00:00Z",
                ),
                types.SimpleNamespace(
                    id="rd-2",
                    caption="Decided project identity belongs in metadata.projectSlug.",
                    metadata={"projectSlug": "memind-main"},
                    created_at="2026-05-26T01:00:00Z",
                ),
            ]
        )

    def query_items(self, user_id, agent_id, **kwargs):
        self.item_calls.append((user_id, agent_id, kwargs))
        categories = kwargs.get("categories") or []
        items = {
            "directive": [
                types.SimpleNamespace(id="it-1", text="Keep userId and agentId stable.", category="directive")
            ],
            "resolution": [
                types.SimpleNamespace(id="it-2", text="Clear retry events only after SUCCESS.", category="resolution")
            ],
            "playbook": [
                types.SimpleNamespace(id="it-3", text="Update all clients after Open API model changes.", category="playbook")
            ],
            "event": [
                types.SimpleNamespace(id="it-4", text="SessionStart currently performs health, replay, cleanup.", category="event")
            ],
        }
        selected = []
        for category in categories:
            selected.extend(items.get(category, []))
        return types.SimpleNamespace(items=selected)


class SessionContextTest(unittest.TestCase):
    def test_build_session_context_queries_current_project_memory(self):
        client = FakeClient()

        context = build_session_context(
            client,
            {"userId": "u", "agentId": "a"},
            "memind-main",
            {
                "sessionContextRecentSessions": 2,
                "sessionContextMaxItems": 3,
                "sessionContextMaxChars": 4000,
            },
        )

        rendered = render_session_context(context, {"sessionContextMaxChars": 4000})
        self.assertIn('<memind_session_context project="memind-main">', rendered)
        self.assertIn("Historical Memind project memory", rendered)
        self.assertIn("Current user instructions and repository files take precedence", rendered)
        self.assertIn("Verify old implementation details against the working tree", rendered)
        self.assertIn("## Continue From", rendered)
        self.assertIn("[rawdata:rd-1, 2026-05-27] Implemented generic memory query APIs", rendered)
        self.assertIn("## Must Follow", rendered)
        self.assertIn("[item:it-1 directive] Keep userId and agentId stable.", rendered)
        self.assertIn("## Watch Outs", rendered)
        self.assertIn("[item:it-2 resolution] Clear retry events only after SUCCESS.", rendered)
        self.assertIn("## Reusable Playbooks", rendered)
        self.assertIn("[item:it-3 playbook] Update all clients", rendered)
        self.assertIn("## Useful Facts", rendered)
        self.assertIn("[item:it-4 event] SessionStart currently performs health", rendered)

        raw_call = client.raw_data_calls[0][2]
        self.assertEqual(raw_call["types"], ["agent_timeline"])
        self.assertEqual(raw_call["metadata_filter"]["all"][0]["path"], "projectSlug")
        self.assertEqual(raw_call["metadata_filter"]["all"][0]["value"], "memind-main")
        self.assertEqual(raw_call["limit"], 2)
        self.assertEqual(raw_call["include"], {"metadata": True, "segment": False})
        self.assertTrue(all(call[2]["raw_data_types"] == ["agent_timeline"] for call in client.item_calls))

    def test_render_session_context_respects_character_budget(self):
        context = {
            "projectSlug": "memind-main",
            "recentRawData": [
                {"id": "rd-1", "caption": "A" * 120},
                {"id": "rd-2", "caption": "B" * 120},
            ],
            "items": {
                "directive": [{"id": "it-1", "category": "directive", "text": "C" * 120}],
                "watchOut": [{"id": "it-2", "category": "resolution", "text": "D" * 120}],
                "playbook": [{"id": "it-3", "category": "playbook", "text": "E" * 120}],
                "fact": [{"id": "it-4", "category": "event", "text": "F" * 120}],
            },
        }

        rendered = render_session_context(context, {"sessionContextMaxChars": 260})

        self.assertLessEqual(len(rendered), 260)
        self.assertIn("truncated", rendered)
        self.assertTrue(rendered.endswith("</memind_session_context>"))


if __name__ == "__main__":
    unittest.main()
