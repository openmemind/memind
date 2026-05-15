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

import unittest

from memind_hermes.content import strip_memind_blocks, text_or_empty


class ContentTest(unittest.TestCase):
    def test_strip_memind_blocks(self):
        text = "hello\n<memind_memories>\nsecret\n</memind_memories>\nworld"

        self.assertEqual(strip_memind_blocks(text).strip(), "hello\n\nworld")

    def test_text_or_empty_handles_none_and_whitespace(self):
        self.assertEqual(text_or_empty(None), "")
        self.assertEqual(text_or_empty("  hello  "), "hello")

    def test_text_or_empty_strips_memind_context(self):
        text = "<memind_memories>memory</memind_memories>remember espresso"

        self.assertEqual(text_or_empty(text), "remember espresso")


if __name__ == "__main__":
    unittest.main()
