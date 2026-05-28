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
sys.path.insert(0, str(ROOT / "scripts"))

from scripts.lib.tool_context import (
    build_metadata_filter,
    current_turn_prompt,
    extract_tool_context_target,
    should_query_tool_context,
)


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


if __name__ == "__main__":
    unittest.main()
