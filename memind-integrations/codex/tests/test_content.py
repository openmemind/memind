import json
import tempfile
import unittest
from pathlib import Path

from scripts.lib.content import extract_messages, fingerprint_message, read_recent_context, strip_memind_blocks


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

    def test_extract_messages_skips_codex_control_context_blocks(self):
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
            messages = extract_messages(path, roles=["user", "assistant"])
        finally:
            path.unlink()
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0]["content"][0]["text"], "real prompt")

    def test_extracts_codex_response_item_messages(self):
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
            messages = extract_messages(path, roles=["user", "assistant"])
        finally:
            path.unlink()
        self.assertEqual([m["role"] for m in messages], ["USER", "ASSISTANT"])
        self.assertEqual(messages[0]["content"][0]["text"], "hello")
        self.assertEqual(messages[1]["content"][0]["text"], "answer\n\nmore")
        self.assertIn("fingerprint", messages[0])

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
            messages = extract_messages(path, roles=["user", "assistant"])
        finally:
            path.unlink()
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0]["content"][0]["text"], "visible")

    def test_fingerprint_prefers_stable_id(self):
        first = fingerprint_message({"payload": {"id": "m1"}, "type": "response_item"}, "USER", "same", 1)
        second = fingerprint_message({"payload": {"id": "m1"}, "type": "response_item"}, "USER", "same", 99)
        self.assertEqual(first, second)

    def test_fingerprint_uses_line_index_to_keep_repeated_messages_distinct(self):
        entry = {"type": "response_item", "payload": {"type": "message"}}
        first = fingerprint_message(entry, "USER", "ok", 1)
        second = fingerprint_message(entry, "USER", "ok", 2)
        self.assertNotEqual(first, second)

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
