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

from scripts.lib.context_compiler import compile_session_start_context


class ContextCompilerTest(unittest.TestCase):
    def test_session_start_context_ranks_dedupes_and_preserves_priority_sections(self):
        context = {
            "projectSlug": "memind-main",
            "recentRawData": [
                {
                    "id": "rd-old",
                    "caption": "Older work on unrelated install docs.",
                    "createdAt": "2026-05-25T10:00:00Z",
                    "metadata": {},
                },
                {
                    "id": "rd-new",
                    "caption": "Completed SessionStart context injection for Claude Code and Codex; keep userId and agentId stable.",
                    "createdAt": "2026-05-27T10:00:00Z",
                    "metadata": {},
                },
            ],
            "items": {
                "directive": [
                    {
                        "id": "dir-1",
                        "category": "directive",
                        "text": "Keep userId and agentId stable; use metadata.projectSlug for project isolation.",
                        "createdAt": "2026-05-27T09:00:00Z",
                        "metadata": {},
                    }
                ],
                "watchOut": [
                    {
                        "id": "res-1",
                        "category": "resolution",
                        "text": "Codex tests must run with Python 3.12; older Python can fail on modern type syntax.",
                        "createdAt": "2026-05-27T08:00:00Z",
                        "metadata": {},
                    },
                    {
                        "id": "res-dup",
                        "category": "resolution",
                        "text": "codex tests must run with python 3.12 older python can fail on modern type syntax",
                        "createdAt": "2026-05-26T08:00:00Z",
                        "metadata": {},
                    },
                ],
                "playbook": [
                    {
                        "id": "pb-1",
                        "category": "playbook",
                        "text": "After changing Claude Code or Codex hooks, run both integration unittest suites and git diff --check.",
                        "createdAt": "2026-05-27T07:00:00Z",
                        "metadata": {},
                    }
                ],
                "fact": [
                    {
                        "id": "fact-1",
                        "category": "event",
                        "text": "SessionStart is read-only: it queries memory and injects context without writing rawdata.",
                        "createdAt": "2026-05-27T06:00:00Z",
                        "metadata": {},
                    }
                ],
            },
        }

        rendered = compile_session_start_context(context, {"sessionContextMaxChars": 6000})

        self.assertIn('<memind_session_context project="memind-main">', rendered)
        self.assertIn("Historical Memind project memory", rendered)
        self.assertIn("Current user instructions and repository files take precedence", rendered)
        self.assertIn("Verify old implementation details against the working tree", rendered)
        self.assertIn("## Continue From", rendered)
        self.assertIn("[rawdata:rd-new, 2026-05-27] Completed SessionStart context injection", rendered)
        self.assertLess(rendered.index("rd-new"), rendered.index("rd-old"))
        self.assertIn("## Must Follow", rendered)
        self.assertIn("[item:dir-1 directive, 2026-05-27] Keep userId and agentId stable", rendered)
        self.assertIn("rd-new", rendered, "turn-summary caption should stay in Continue From")
        self.assertIn("dir-1", rendered, "structured item should not be removed by caption text")
        self.assertIn("## Watch Outs", rendered)
        self.assertIn("[item:res-1 resolution, 2026-05-27] Codex tests must run", rendered)
        self.assertNotIn("res-dup", rendered)
        self.assertIn("## Reusable Playbooks", rendered)
        self.assertIn("[item:pb-1 playbook, 2026-05-27] After changing Claude Code", rendered)
        self.assertIn("## Useful Facts", rendered)
        self.assertIn("[item:fact-1 event, 2026-05-27] SessionStart is read-only", rendered)
        self.assertTrue(rendered.endswith("</memind_session_context>"))

    def test_session_start_context_uses_section_budget_instead_of_naive_line_truncation(self):
        context = {
            "projectSlug": "memind-main",
            "recentRawData": [
                {"id": "rd-1", "caption": "Recent work " + "A" * 500, "createdAt": "2026-05-27T01:00:00Z"},
            ],
            "items": {
                "directive": [
                    {"id": "dir-1", "category": "directive", "text": "Do not break stable identity.", "createdAt": "2026-05-27T01:00:00Z"}
                ],
                "watchOut": [
                    {"id": "res-1", "category": "resolution", "text": "Always avoid committing __pycache__ files.", "createdAt": "2026-05-27T01:00:00Z"}
                ],
                "playbook": [
                    {"id": "pb-1", "category": "playbook", "text": "Run focused integration tests after hook changes.", "createdAt": "2026-05-27T01:00:00Z"}
                ],
                "fact": [
                    {"id": "fact-1", "category": "event", "text": "Fact " + "F" * 400, "createdAt": "2026-05-27T01:00:00Z"}
                ],
            },
        }

        rendered = compile_session_start_context(context, {"sessionContextMaxChars": 900})

        self.assertLessEqual(len(rendered), 900)
        self.assertIn("## Must Follow", rendered)
        self.assertIn("Do not break stable identity.", rendered)
        self.assertIn("## Watch Outs", rendered)
        self.assertIn("Always avoid committing __pycache__", rendered)
        self.assertIn("truncated: lower-priority memories omitted", rendered)
        self.assertTrue(rendered.endswith("</memind_session_context>"))

    def test_session_start_context_returns_empty_when_no_entries_exist(self):
        rendered = compile_session_start_context(
            {"projectSlug": "memind-main", "recentRawData": [], "items": {}},
            {"sessionContextMaxChars": 6000},
        )

        self.assertEqual(rendered, "")

    def test_prompt_retrieval_context_groups_agent_categories_by_execution_value(self):
        from scripts.lib.context_compiler import compile_prompt_retrieval_context

        data = {
            "insights": [
                {"id": "ins-leaf", "text": "Leaf insight", "tier": "LEAF"},
                {"id": "ins-root", "text": "Root insight", "tier": "ROOT"},
            ],
            "items": [
                {"id": "tool-1", "text": "Use mvn -pl memind-server test for server checks.", "category": "tool", "finalScore": 0.6},
                {"id": "res-1", "text": "Retry spool events are cleared only after successful agent_timeline extraction.", "category": "resolution", "finalScore": 0.9},
                {"id": "pb-1", "text": "When hooks change, run both integration test suites.", "category": "playbook", "finalScore": 0.8},
                {"id": "dir-1", "text": "Do not default Claude Code or Codex to conversation rawdata.", "category": "directive", "finalScore": 0.7},
                {"id": "ev-1", "text": "rawdata-agent emits agent_episode segment metadata.", "category": "event", "finalScore": 0.5},
                {"id": "ev-high", "text": "A high-scoring general fact should not crowd out agent-specific sections.", "category": "event", "finalScore": 0.99},
            ],
        }

        rendered = compile_prompt_retrieval_context(
            data,
            {"retrieveMaxEntries": 8, "retrieveMaxChars": 6000, "retrievePromptPreamble": "Relevant memories from Memind."},
        )

        self.assertIn("<memind_memories>", rendered)
        self.assertIn("## Directives", rendered)
        self.assertIn("[item:dir-1 directive] Do not default Claude Code", rendered)
        self.assertIn("## Resolved Problems", rendered)
        self.assertIn("[item:res-1 resolution] Retry spool events", rendered)
        self.assertIn("## Agent Playbooks", rendered)
        self.assertIn("[item:pb-1 playbook] When hooks change", rendered)
        self.assertIn("## Tool Notes", rendered)
        self.assertIn("[item:tool-1 tool] Use mvn", rendered)
        self.assertIn("## Insights", rendered)
        self.assertIn("[insight:ins-root root] Root insight", rendered)
        self.assertNotIn("ins-leaf", rendered)
        self.assertIn("## Memory Items", rendered)
        self.assertIn("[item:ev-high event] A high-scoring general fact", rendered)
        self.assertIn("[item:ev-1 event] rawdata-agent emits", rendered)
        self.assertTrue(rendered.endswith("</memind_memories>"))

    def test_prompt_retrieval_context_preserves_insight_tier_order(self):
        from scripts.lib.context_compiler import compile_prompt_retrieval_context

        rendered = compile_prompt_retrieval_context(
            {
                "insights": [
                    {"id": "leaf", "text": "Leaf memory", "tier": "LEAF"},
                    {"id": "root", "text": "Root memory", "tier": "ROOT"},
                    {"id": "branch", "text": "Branch memory", "tier": "BRANCH"},
                ]
            },
            {"retrieveMaxEntries": 8, "retrieveMaxChars": 6000, "retrievePromptPreamble": ""},
        )

        self.assertLess(rendered.index("insight:root"), rendered.index("insight:branch"))
        self.assertNotIn("insight:leaf", rendered)

    def test_prompt_retrieval_context_keeps_degraded_notice(self):
        from scripts.lib.context_compiler import compile_prompt_retrieval_context

        rendered = compile_prompt_retrieval_context(
            {"status": "degraded"},
            {"retrieveMaxEntries": 8, "retrieveMaxChars": 1000, "retrievePromptPreamble": ""},
        )

        self.assertIn("Memory retrieval encountered an error", rendered)
        self.assertIn("<memind_memories>", rendered)

    def test_prompt_context_renders_project_first_attrs_and_source_labels(self):
        from scripts.lib.context_compiler import compile_prompt_retrieval_context

        rendered = compile_prompt_retrieval_context(
            {
                "projectSlug": "memind-main",
                "mode": "project-first",
                "items": [
                    {
                        "id": "dir-1",
                        "text": "Keep userId and agentId stable.",
                        "category": "directive",
                        "createdAt": "2026-05-27T10:00:00Z",
                        "finalScore": 0.9,
                        "memindContextSource": "project",
                    },
                    {
                        "id": "beh-1",
                        "text": "User prefers Chinese replies.",
                        "category": "behavior",
                        "createdAt": "2026-05-20T10:00:00Z",
                        "finalScore": 0.88,
                        "memindContextSource": "global",
                    },
                ],
                "insights": [
                    {
                        "id": "ins-1",
                        "text": "Run both Claude Code and Codex tests after hook edits.",
                        "tier": "root",
                        "createdAt": "2026-05-18T10:00:00Z",
                        "memindContextSource": "shared",
                    }
                ],
            },
            {
                "retrieveMaxEntries": 8,
                "retrieveMaxChars": 6000,
                "retrievePromptPreamble": "Relevant memories from Memind.",
            },
        )

        self.assertIn('<memind_memories project="memind-main" mode="project-first">', rendered)
        self.assertIn("[item:dir-1 directive, project, 2026-05-27]", rendered)
        self.assertIn("[item:beh-1 behavior, global, 2026-05-20]", rendered)
        self.assertIn("[insight:ins-1 root, shared, 2026-05-18]", rendered)

    def test_tool_context_compiler_renders_bounded_file_context(self):
        from scripts.lib.context_compiler import compile_tool_context

        rendered = compile_tool_context(
            {
                "target": {
                    "toolName": "Edit",
                    "kind": "file_edit",
                    "path": "src/payment/calc.ts",
                    "projectSlug": "payment-service-abc",
                },
                "items": [
                    {
                        "id": "res-1",
                        "category": "resolution",
                        "text": "rounding mismatch was resolved in src/payment/calc.ts and validated with npm test payment.",
                        "metadata": {
                            "files": ["src/payment/calc.ts"],
                            "commands": ["npm test payment"],
                        },
                    },
                    {
                        "id": "tool-1",
                        "category": "tool",
                        "text": "Use npm test payment to validate changes touching src/payment/calc.ts; it failed once and passed once in this agent episode.",
                        "metadata": {
                            "files": ["src/payment/calc.ts"],
                            "commands": ["npm test payment"],
                            "toolStats": {"Bash": {"successCount": 1, "failCount": 1}},
                        },
                    },
                    {
                        "id": "pb-1",
                        "category": "playbook",
                        "text": "When payment calculation logic changes, update focused tests first, then run npm test payment.",
                        "metadata": {},
                    },
                ],
                "rawData": [
                    {
                        "id": "rd-1",
                        "caption": "Edited src/payment/calc.ts and validated npm test payment.",
                        "metadata": {
                            "toolStats": {"Bash": {"successCount": 1, "failCount": 1}},
                        },
                    }
                ],
            },
            {"toolContextMaxChars": 3500, "toolContextEntryMaxChars": 520},
        )

        self.assertIn('<memind_tool_context tool="Edit" file="src/payment/calc.ts"', rendered)
        self.assertIn("Use only if directly relevant to this exact tool call", rendered)
        self.assertIn("## Prior Resolutions", rendered)
        self.assertIn("[item:res-1 resolution]", rendered)
        self.assertIn("## Validation Notes", rendered)
        self.assertIn("[item:tool-1 tool]", rendered)
        self.assertIn("## Relevant Playbooks", rendered)
        self.assertIn("[item:pb-1 playbook]", rendered)
        self.assertIn("## Recent Evidence", rendered)
        self.assertIn("[rawdata:rd-1]", rendered)
        self.assertNotIn("durationMs", rendered)
        self.assertNotIn("inputTokens", rendered)
        self.assertNotIn("outputTokens", rendered)
        self.assertTrue(rendered.endswith("</memind_tool_context>"))

    def test_tool_context_compiler_renders_command_context_with_budget(self):
        from scripts.lib.context_compiler import compile_tool_context

        rendered = compile_tool_context(
            {
                "target": {
                    "toolName": "Bash",
                    "kind": "test_result",
                    "command": "npm test payment",
                    "projectSlug": "payment-service-abc",
                },
                "items": [
                    {
                        "id": "tool-1",
                        "category": "tool",
                        "text": "Use npm test payment after editing payment calculation files. " + "x" * 900,
                        "metadata": {"commands": ["npm test payment"]},
                    },
                    {
                        "id": "dir-1",
                        "category": "directive",
                        "text": "Do not skip focused payment validation after touching calculation code.",
                        "metadata": {},
                    },
                ],
                "rawData": [],
            },
            {"toolContextMaxChars": 900, "toolContextEntryMaxChars": 260},
        )

        self.assertLessEqual(len(rendered), 900)
        self.assertIn('<memind_tool_context tool="Bash" command="npm test payment"', rendered)
        self.assertIn("## Validation Notes", rendered)
        self.assertIn("## Directives", rendered)
        self.assertTrue(rendered.endswith("</memind_tool_context>"))


if __name__ == "__main__":
    unittest.main()
