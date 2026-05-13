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


class MemindClient:
    def __init__(self, base_url, token=None, timeout=10, max_retries=0):
        self.base_url = base_url
        self.token = token
        self.timeout = timeout
        self.max_retries = max_retries

    def _async_client(self):
        from memind import AsyncMemindClient

        return AsyncMemindClient(
            base_url=self.base_url,
            api_token=self.token,
            timeout=self.timeout,
            max_retries=self.max_retries,
        )

    async def health(self):
        async with self._async_client() as client:
            return await client.health()

    async def extract(self, user_id, agent_id, raw_content, source_client=None):
        async with self._async_client() as client:
            return await client.memory.extract(
                user_id=user_id,
                agent_id=agent_id,
                raw_content=raw_content,
                source_client=source_client,
            )

    async def add_message(self, user_id, agent_id, message, source_client=None):
        async with self._async_client() as client:
            return await client.memory.add_message(
                user_id=user_id,
                agent_id=agent_id,
                message=message,
                source_client=source_client,
            )

    async def commit(self, user_id, agent_id, source_client=None):
        async with self._async_client() as client:
            return await client.memory.commit(
                user_id=user_id,
                agent_id=agent_id,
                source_client=source_client,
            )

    def retrieve(self, user_id, agent_id, query, strategy="SIMPLE", trace=False):
        from memind import MemindClient as OfficialMemindClient

        with OfficialMemindClient(
            base_url=self.base_url,
            api_token=self.token,
            timeout=self.timeout,
            max_retries=self.max_retries,
        ) as client:
            return client.memory.retrieve(
                user_id=user_id,
                agent_id=agent_id,
                query=query,
                strategy=strategy,
                trace=trace,
            )
