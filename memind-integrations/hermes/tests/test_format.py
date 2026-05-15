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

import types
import unittest

from memind_hermes.format import format_memind_context


class FormatTest(unittest.TestCase):
    def test_prioritizes_root_branch_insights_then_items(self):
        data = {
            "insights": [
                {"id": "3", "text": "leaf", "tier": "LEAF"},
                {"id": "2", "text": "branch", "tier": "BRANCH"},
                {"id": "1", "text": "root", "tier": "ROOT"},
            ],
            "items": [
                {"id": "10", "text": "low", "finalScore": 0.1},
                {"id": "11", "text": "high", "finalScore": 0.9},
            ],
        }

        context = format_memind_context(
            data,
            {"retrieveMaxEntries": 4, "retrieveMaxChars": 1000, "retrievePromptPreamble": "P"},
        )

        self.assertIn("<memind_memories>", context)
        self.assertLess(context.index("[insight:1] root"), context.index("[insight:2] branch"))
        self.assertNotIn("leaf", context)
        self.assertLess(context.index("[item:11] high"), context.index("[item:10] low"))

    def test_accepts_pydantic_like_response(self):
        response = types.SimpleNamespace(
            model_dump=lambda by_alias=True: {
                "insights": [{"id": "1", "text": "root", "tier": "ROOT"}],
                "items": [],
            }
        )

        context = format_memind_context(response, {"retrievePromptPreamble": "P"})

        self.assertIn("[insight:1] root", context)

    def test_includes_degraded_notice_without_results(self):
        context = format_memind_context({"status": "degraded"}, {"retrievePromptPreamble": ""})

        self.assertIn("Memory retrieval encountered an error", context)

    def test_truncates_body(self):
        data = {"items": [{"id": "1", "text": "x" * 100, "finalScore": 1.0}]}

        context = format_memind_context(
            data,
            {"retrievePromptPreamble": "P", "retrieveMaxChars": 40, "retrieveMaxEntries": 8},
        )

        self.assertIn("[truncated]", context)


if __name__ == "__main__":
    unittest.main()
