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
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts"))


class PromptContextTests(unittest.TestCase):
    def test_project_hits_skip_global_fallback(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeResponse:
            def __init__(self, items=None, insights=None):
                self.items = items or []
                self.insights = insights or []
                self.raw_data = []

            def model_dump(self, by_alias=True):
                return {
                    "items": self.items,
                    "insights": self.insights,
                    "rawData": self.raw_data,
                }

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                return FakeResponse(
                    items=[
                        {
                            "id": "p1",
                            "text": "Project directive",
                            "category": "directive",
                            "metadata": {"projectSlug": "memind-main"},
                            "finalScore": 0.9,
                        },
                        {
                            "id": "p2",
                            "text": "Project resolution",
                            "category": "resolution",
                            "metadata": {"projectSlug": "memind-main"},
                            "finalScore": 0.8,
                        },
                    ],
                    insights=[
                        {"id": "i1", "text": "Project insight", "tier": "root"},
                        {"id": "i2", "text": "Project branch", "tier": "branch"},
                    ],
                )

        client = FakeClient()
        result = build_prompt_context(
            client,
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 4,
                "promptContextGlobalFallbackEntries": 3,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual(len(client.calls), 1)
        self.assertEqual(client.calls[0]["metadata_filter"]["all"][0]["path"], "projectSlug")
        self.assertEqual(client.calls[0]["metadata_filter"]["all"][0]["value"], "memind-main")
        self.assertEqual(result["mode"], "project-first")
        self.assertEqual(result["projectSlug"], "memind-main")
        self.assertTrue(all(item["memindContextSource"] == "project" for item in result["items"]))
        self.assertTrue(all(insight["memindContextSource"] == "project" for insight in result["insights"]))

    def test_sparse_project_hits_use_bounded_global_fallback(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeResponse:
            def __init__(self, items=None, insights=None):
                self.items = items or []
                self.insights = insights or []
                self.raw_data = []

            def model_dump(self, by_alias=True):
                return {
                    "items": self.items,
                    "insights": self.insights,
                    "rawData": self.raw_data,
                }

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                if len(self.calls) == 1:
                    return FakeResponse(
                        items=[
                            {
                                "id": "same",
                                "text": "Project directive",
                                "category": "directive",
                                "metadata": {"projectSlug": "memind-main"},
                                "finalScore": 0.9,
                            }
                        ],
                    )
                return FakeResponse(
                    items=[
                        {
                            "id": "same",
                            "text": "Duplicate project directive",
                            "category": "directive",
                            "metadata": {"projectSlug": "memind-main"},
                            "finalScore": 0.95,
                        },
                        {
                            "id": "g1",
                            "text": "User prefers Chinese replies",
                            "category": "behavior",
                            "metadata": {},
                            "finalScore": 0.91,
                        },
                        {
                            "id": "s1",
                            "text": "Run both integration tests after hook edits",
                            "category": "tool",
                            "metadata": {"projectSlug": "other-project"},
                            "finalScore": 0.8,
                        },
                        {
                            "id": "low",
                            "text": "Weak unrelated memory",
                            "category": "event",
                            "metadata": {},
                            "finalScore": 0.2,
                        },
                    ],
                    insights=[
                        {"id": "gi1", "text": "Shared testing insight", "tier": "root", "metadata": {}}
                    ],
                )

        client = FakeClient()
        result = build_prompt_context(
            client,
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 4,
                "promptContextGlobalFallbackEntries": 3,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual(len(client.calls), 2)
        self.assertIsNone(client.calls[1].get("metadata_filter"))
        item_ids = [item["id"] for item in result["items"]]
        self.assertEqual(item_ids, ["same", "g1", "s1"])
        sources = {item["id"]: item["memindContextSource"] for item in result["items"]}
        self.assertEqual(sources["same"], "project")
        self.assertEqual(sources["g1"], "global")
        self.assertEqual(sources["s1"], "shared")
        self.assertNotIn("low", item_ids)
        self.assertEqual(result["insights"][0]["memindContextSource"], "global")

    def test_dict_retrieve_response_is_supported(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                return {
                    "items": [
                        {
                            "id": "p1",
                            "text": "Project directive",
                            "category": "directive",
                            "metadata": {"projectSlug": "memind-main"},
                            "finalScore": 0.9,
                        }
                    ],
                    "insights": [{"id": "i1", "text": "Project insight", "tier": "root"}],
                    "rawData": [],
                }

        result = build_prompt_context(
            FakeClient(),
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 2,
                "promptContextGlobalFallbackEntries": 3,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual([item["id"] for item in result["items"]], ["p1"])
        self.assertEqual(result["items"][0]["memindContextSource"], "project")
        self.assertEqual(result["insights"][0]["memindContextSource"], "project")

    def test_fallback_limit_is_not_consumed_by_project_duplicates(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeResponse:
            def __init__(self, items=None):
                self.items = items or []
                self.insights = []
                self.raw_data = []

            def model_dump(self, by_alias=True):
                return {"items": self.items, "insights": self.insights, "rawData": self.raw_data}

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                if len(self.calls) == 1:
                    return FakeResponse(
                        items=[
                            {
                                "id": "same",
                                "text": "Project directive",
                                "category": "directive",
                                "metadata": {"projectSlug": "memind-main"},
                                "finalScore": 0.9,
                            }
                        ]
                    )
                return FakeResponse(
                    items=[
                        {
                            "id": "same",
                            "text": "Duplicate without project metadata",
                            "category": "directive",
                            "metadata": {},
                            "finalScore": 0.95,
                        },
                        {
                            "id": "g1",
                            "text": "Reusable global memory one",
                            "category": "behavior",
                            "metadata": {},
                            "finalScore": 0.9,
                        },
                        {
                            "id": "g2",
                            "text": "Reusable global memory two",
                            "category": "tool",
                            "metadata": {},
                            "finalScore": 0.88,
                        },
                    ]
                )

        result = build_prompt_context(
            FakeClient(),
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 4,
                "promptContextGlobalFallbackEntries": 2,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual([item["id"] for item in result["items"]], ["same", "g1", "g2"])

    def test_empty_project_entries_do_not_skip_fallback(self):
        from scripts.lib.prompt_context import build_prompt_context

        class FakeResponse:
            def __init__(self, items=None, insights=None):
                self.items = items or []
                self.insights = insights or []
                self.raw_data = []

            def model_dump(self, by_alias=True):
                return {"items": self.items, "insights": self.insights, "rawData": self.raw_data}

        class FakeClient:
            def __init__(self):
                self.calls = []

            def retrieve(self, *args, **kwargs):
                self.calls.append(kwargs)
                if len(self.calls) == 1:
                    return FakeResponse(
                        items=[
                            {"id": "empty-item-1", "text": "", "category": "directive"},
                            {"id": "empty-item-2", "category": "resolution"},
                        ],
                        insights=[
                            {"id": "empty-insight-1", "text": ""},
                            {"id": "empty-insight-2"},
                        ],
                    )
                return FakeResponse(
                    items=[
                        {
                            "id": "g1",
                            "text": "Reusable global memory",
                            "category": "behavior",
                            "metadata": {},
                            "finalScore": 0.9,
                        }
                    ]
                )

        client = FakeClient()
        result = build_prompt_context(
            client,
            {"userId": "u", "agentId": "a"},
            "fix retrieval",
            "memind-main",
            {
                "retrieveStrategy": "SIMPLE",
                "promptContextProjectMinEntries": 4,
                "promptContextGlobalFallbackEntries": 3,
                "promptContextGlobalFallbackMinScore": 0.65,
            },
        )

        self.assertEqual(len(client.calls), 2)
        self.assertEqual([item["id"] for item in result["items"] if item.get("text")], ["g1"])


if __name__ == "__main__":
    unittest.main()
