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


if __name__ == "__main__":
    unittest.main()
