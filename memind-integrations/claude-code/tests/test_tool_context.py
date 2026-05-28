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
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from scripts.lib.tool_context import (
    build_metadata_filter,
    current_turn_prompt,
    extract_tool_context_target,
    should_query_tool_context,
)


class FakeClient:
    def __init__(self):
        self.item_queries = []
        self.raw_queries = []
        self.retrieve_queries = []

    def query_items(self, **kwargs):
        self.item_queries.append(kwargs)
        if kwargs.get("metadata_filter", {}).get("all"):
            return SimpleNamespace(
                items=[
                    SimpleNamespace(
                        id="res-1",
                        text="rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                        category="resolution",
                        created_at="2026-05-27T10:00:00Z",
                        metadata={
                            "projectSlug": "payment-service-abc",
                            "files": ["src/payment/calc.ts"],
                            "commands": ["npm test payment"],
                        },
                    )
                ]
            )
        return SimpleNamespace(items=[])

    def query_raw_data(self, **kwargs):
        self.raw_queries.append(kwargs)
        return SimpleNamespace(
            raw_data=[
                SimpleNamespace(
                    id="rd-1",
                    caption="Edited src/payment/calc.ts and validated npm test payment.",
                    type="agent_timeline",
                    created_at="2026-05-27T10:05:00Z",
                    metadata={
                        "projectSlug": "payment-service-abc",
                        "files": ["src/payment/calc.ts"],
                        "commands": ["npm test payment"],
                        "toolStats": {"Bash": {"successCount": 1, "failCount": 1}},
                    },
                )
            ]
        )

    def retrieve(self, *args, **kwargs):
        self.retrieve_queries.append(kwargs)
        return SimpleNamespace(items=[], insights=[], raw_data=[])


class ToolContextTest(unittest.TestCase):
    def test_extracts_file_edit_target(self):
        target = extract_tool_context_target(
            {
                "kind": "file_edit",
                "toolName": "Edit",
                "path": "src/payment/calc.ts",
                "operation": "edit",
                "metadata": {"turnId": "s-turn-1"},
            },
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertEqual(target["toolName"], "Edit")
        self.assertEqual(target["kind"], "file_edit")
        self.assertEqual(target["path"], "src/payment/calc.ts")
        self.assertEqual(target["projectSlug"], "payment-service-abc")

    def test_extracts_command_target(self):
        target = extract_tool_context_target(
            {
                "kind": "test_result",
                "toolName": "Bash",
                "command": "npm test payment",
                "operation": "run",
                "metadata": {"validationType": "test"},
            },
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertEqual(target["command"], "npm test payment")
        self.assertEqual(target["validationType"], "test")

    def test_skips_low_value_tools(self):
        target = extract_tool_context_target(
            {"kind": "file_read", "toolName": "Read", "path": "README.md"},
            {"cwd": "/repo/payment"},
            "payment-service-abc",
        )

        self.assertFalse(should_query_tool_context(target, {"autoToolContext": True}))

    def test_skips_when_disabled_or_no_target(self):
        self.assertFalse(should_query_tool_context({}, {"autoToolContext": True}))
        self.assertFalse(
            should_query_tool_context(
                {"kind": "file_edit", "path": "src/a.ts"}, {"autoToolContext": False}
            )
        )

    def test_builds_top_level_metadata_filter(self):
        metadata_filter = build_metadata_filter(
            {
                "projectSlug": "payment-service-abc",
                "path": "src/payment/calc.ts",
                "command": "npm test payment",
                "toolName": "Bash",
            },
            include_project=True,
        )

        self.assertEqual(
            metadata_filter["all"],
            [{"path": "projectSlug", "op": "eq", "value": "payment-service-abc"}],
        )
        self.assertIn(
            {"path": "files", "op": "contains", "value": "src/payment/calc.ts"},
            metadata_filter["any"],
        )
        self.assertIn(
            {"path": "commands", "op": "contains", "value": "npm test payment"},
            metadata_filter["any"],
        )
        self.assertIn(
            {"path": "toolNames", "op": "contains", "value": "Bash"},
            metadata_filter["any"],
        )

    def test_current_turn_prompt_uses_matching_turn_id(self):
        prompt = current_turn_prompt(
            [
                {"kind": "user_prompt", "text": "older", "metadata": {"turnId": "t0"}},
                {"kind": "user_prompt", "text": "Fix payment tests", "metadata": {"turnId": "t1"}},
                {"kind": "file_edit", "metadata": {"turnId": "t1"}},
            ],
            "t1",
        )

        self.assertEqual(prompt, "Fix payment tests")

    def test_load_tool_context_uses_exact_queries_first(self):
        from scripts.lib.tool_context import load_tool_context

        client = FakeClient()
        context = load_tool_context(
            client,
            "u",
            "a",
            {
                "toolName": "Edit",
                "kind": "file_edit",
                "path": "src/payment/calc.ts",
                "projectSlug": "payment-service-abc",
                "prompt": "Fix payment tests",
            },
            {"toolContextMaxItems": 6, "toolContextMinExactItems": 2},
        )

        self.assertEqual(len(client.item_queries), 2)
        self.assertEqual(len(client.raw_queries), 1)
        self.assertEqual(client.item_queries[0]["categories"], ["resolution", "tool", "playbook", "directive"])
        self.assertEqual(client.item_queries[0]["metadata_filter"]["all"][0]["path"], "projectSlug")
        self.assertEqual(client.raw_queries[0]["types"], ["agent_timeline"])
        self.assertEqual(context["target"]["path"], "src/payment/calc.ts")
        self.assertEqual(context["items"][0]["category"], "resolution")
        self.assertEqual(context["rawData"][0]["id"], "rd-1")

    def test_load_tool_context_uses_retrieve_fallback_when_exact_hits_are_sparse(self):
        from scripts.lib.tool_context import load_tool_context

        class SparseClient(FakeClient):
            def query_items(self, **kwargs):
                self.item_queries.append(kwargs)
                return SimpleNamespace(items=[])

            def query_raw_data(self, **kwargs):
                self.raw_queries.append(kwargs)
                return SimpleNamespace(raw_data=[])

            def retrieve(self, *args, **kwargs):
                self.retrieve_queries.append(kwargs)
                return SimpleNamespace(
                    items=[
                        SimpleNamespace(
                            id="tool-1",
                            text="Use npm test payment after editing payment calculation files.",
                            category="tool",
                            created_at="2026-05-27T10:00:00Z",
                            metadata={"commands": ["npm test payment"]},
                        )
                    ],
                    insights=[],
                    raw_data=[],
                )

        client = SparseClient()
        context = load_tool_context(
            client,
            "u",
            "a",
            {
                "toolName": "Bash",
                "kind": "test_result",
                "command": "npm test payment",
                "projectSlug": "payment-service-abc",
                "prompt": "Fix payment tests",
            },
            {"toolContextMaxItems": 6, "toolContextMinExactItems": 1},
        )

        self.assertEqual(len(client.retrieve_queries), 1)
        self.assertEqual(client.retrieve_queries[0]["categories"], ["resolution", "tool", "playbook", "directive"])
        self.assertEqual(context["items"][0]["id"], "tool-1")


if __name__ == "__main__":
    unittest.main()
