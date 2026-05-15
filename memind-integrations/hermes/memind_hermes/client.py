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


def build_conversation_content(role_text_pairs):
    if not role_text_pairs:
        raise ValueError("conversation content requires at least one message")
    from memind import ConversationContent, Message

    messages = []
    for role, text in role_text_pairs:
        normalized = str(role).upper()
        if normalized == "ASSISTANT":
            messages.append(Message.assistant(text))
        else:
            messages.append(Message.user(text))
    return ConversationContent(messages=messages)


class MemindClient:
    def __init__(self, base_url, token=None, timeout=10, max_retries=0):
        self.base_url = base_url
        self.token = token
        self.timeout = timeout
        self.max_retries = max_retries

    def _sync_client(self):
        from memind import MemindClient as OfficialMemindClient

        return OfficialMemindClient(
            base_url=self.base_url,
            api_token=self.token,
            timeout=self.timeout,
            max_retries=self.max_retries,
        )

    def health(self):
        with self._sync_client() as client:
            return client.health()

    def retrieve(self, user_id, agent_id, query, strategy="SIMPLE", trace=False):
        with self._sync_client() as client:
            return client.memory.retrieve(
                user_id=user_id,
                agent_id=agent_id,
                query=query,
                strategy=strategy,
                trace=trace,
            )

    def extract(self, user_id, agent_id, raw_content, source_client=None):
        with self._sync_client() as client:
            return client.memory.extract(
                user_id=user_id,
                agent_id=agent_id,
                raw_content=raw_content,
                source_client=source_client,
            )
