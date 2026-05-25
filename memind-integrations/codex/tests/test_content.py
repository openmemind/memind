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

from scripts.lib.content import read_recent_context, strip_memind_blocks


class ContentTest(unittest.TestCase):
    def write_jsonl(self, entries):
        handle = tempfile.NamedTemporaryFile("w", delete=False)
        with handle:
            handle.write("not-json\n")
            for entry in entries:
                handle.write(json.dumps(entry) + "\n")
        return Path(handle.name)

    def test_strip_memind_blocks(self):
        text = "before <memind_memories>secret</memind_memories> after"
        self.assertEqual(strip_memind_blocks(text), "before  after")

    def test_read_recent_context_skips_codex_control_context_blocks(self):
        path = self.write_jsonl(
            [
                {
                    "type": "response_item",
                    "payload": {
                        "type": "message",
                        "role": "user",
                        "content": [{"type": "input_text", "text": "<environment_context>\n  <cwd>/tmp/project</cwd>\n</environment_context>"}],
                    },
                },
                {"role": "user", "content": "real prompt"},
            ]
        )
        try:
            context = read_recent_context(path, turns=2)
        finally:
            path.unlink()
        self.assertEqual(context, "user: real prompt")

    def test_read_recent_context_reads_codex_response_item_messages(self):
        path = self.write_jsonl(
            [
                {
                    "type": "response_item",
                    "payload": {
                        "type": "message",
                        "role": "user",
                        "id": "u1",
                        "content": [{"type": "input_text", "text": "hello"}],
                    },
                },
                {
                    "type": "response_item",
                    "payload": {
                        "type": "message",
                        "role": "assistant",
                        "phase": "final_answer",
                        "id": "a1",
                        "content": [
                            {"type": "output_text", "text": "answer"},
                            {"type": "output_text", "text": "more"},
                        ],
                    },
                },
            ]
        )
        try:
            context = read_recent_context(path, turns=2)
        finally:
            path.unlink()
        self.assertEqual(context, "user: hello\nassistant: answer\n\nmore")

    def test_skips_non_final_assistant_and_tool_calls(self):
        path = self.write_jsonl(
            [
                {
                    "type": "response_item",
                    "payload": {
                        "type": "message",
                        "role": "assistant",
                        "phase": "analysis",
                        "content": [{"type": "output_text", "text": "hidden"}],
                    },
                },
                {"type": "response_item", "payload": {"type": "function_call", "name": "shell"}},
                {"type": "response_item", "payload": {"type": "function_call_output", "output": "large output"}},
                {"type": "event_msg", "message": "tool event"},
                {"role": "user", "content": "visible"},
            ]
        )
        try:
            context = read_recent_context(path, turns=2)
        finally:
            path.unlink()
        self.assertEqual(context, "user: visible")

    def test_read_recent_context_reads_tail(self):
        entries = [{"role": "user", "content": f"message-{i}"} for i in range(20)]
        path = self.write_jsonl(entries)
        try:
            context = read_recent_context(path, turns=2)
        finally:
            path.unlink()
        self.assertIn("message-19", context)
        self.assertIn("message-18", context)
        self.assertNotIn("message-1\n", context)


if __name__ == "__main__":
    unittest.main()
