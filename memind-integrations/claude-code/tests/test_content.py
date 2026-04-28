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

import json
import tempfile
import unittest
from pathlib import Path

from scripts.lib.content import extract_messages, read_recent_context, strip_memind_blocks


class ContentTest(unittest.TestCase):
    def test_strip_memind_blocks(self):
        text = "before <memind_memories>secret</memind_memories> after"
        self.assertEqual(strip_memind_blocks(text), "before  after")

    def test_extract_messages_skips_tools_and_unknown_lines(self):
        lines = [
            {"type": "user", "timestamp": "2026-04-28T00:00:00Z", "message": {"content": "hello"}},
            {
                "type": "assistant",
                "message": {
                    "content": [
                        {"type": "text", "text": "answer"},
                        {"type": "text", "text": "more"},
                        {"type": "tool_use", "name": "Read", "input": {"file_path": "a.py"}},
                        {"type": "thinking", "thinking": "hidden"},
                    ]
                },
            },
            {"type": "user", "message": {"content": [{"type": "tool_result", "content": "file"}]}},
            {"type": "system", "subtype": "turn_duration"},
            {"type": "unknown", "message": {"content": "skip"}},
        ]
        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            handle.write("not-json\n")
            for entry in lines:
                handle.write(json.dumps(entry) + "\n")
            path = Path(handle.name)
        try:
            messages = extract_messages(path, roles=["user", "assistant"])
        finally:
            path.unlink()
        self.assertEqual([m["role"] for m in messages], ["USER", "ASSISTANT"])
        self.assertEqual(messages[0]["content"][0]["text"], "hello")
        self.assertEqual(messages[1]["content"][0]["text"], "answer\n\nmore")


    def test_extract_messages_skips_claude_code_interrupt_placeholders(self):
        lines = [
            {"type": "user", "message": {"content": "[Request interrupted by user]"}},
            {"type": "assistant", "message": {"content": "[Request interrupted by user]"}},
            {"type": "user", "message": {"content": "real instruction"}},
        ]
        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            for entry in lines:
                handle.write(json.dumps(entry) + "\n")
            path = Path(handle.name)
        try:
            messages = extract_messages(path, roles=["user", "assistant"])
        finally:
            path.unlink()
        self.assertEqual([m["content"][0]["text"] for m in messages], ["real instruction"])

    def test_read_recent_context_reads_tail(self):
        with tempfile.NamedTemporaryFile("w", delete=False) as handle:
            for i in range(20):
                entry = {"type": "user", "message": {"content": f"message-{i}"}}
                handle.write(json.dumps(entry) + "\n")
            path = Path(handle.name)
        try:
            context = read_recent_context(path, turns=2)
        finally:
            path.unlink()
        self.assertIn("message-19", context)
        self.assertIn("message-18", context)
        self.assertNotIn("message-1\n", context)


if __name__ == "__main__":
    unittest.main()
