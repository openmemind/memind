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

    def retrieve(
        self,
        user_id,
        agent_id,
        query,
        strategy="SIMPLE",
        trace=False,
        scope=None,
        categories=None,
        time_range=None,
        metadata_filter=None,
        include=None,
    ):
        from memind import (
            MemindClient as OfficialMemindClient,
            MetadataFilter,
            RetrieveIncludeOptions,
            TimeRange,
        )

        metadata_filter_obj = (
            MetadataFilter(**metadata_filter)
            if isinstance(metadata_filter, dict)
            else metadata_filter
        )
        include_obj = (
            RetrieveIncludeOptions(**include)
            if isinstance(include, dict)
            else include
        )
        time_range_obj = TimeRange(**time_range) if isinstance(time_range, dict) else time_range

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
                scope=scope,
                categories=categories,
                time_range=time_range_obj,
                metadata_filter=metadata_filter_obj,
                include=include_obj,
            )

    def query_items(
        self,
        user_id,
        agent_id,
        scope=None,
        categories=None,
        source_clients=None,
        raw_data_types=None,
        time_range=None,
        metadata_filter=None,
        limit=None,
        cursor=None,
    ):
        from memind import MemindClient as OfficialMemindClient
        from memind import QueryMemoryItemsRequest

        request = QueryMemoryItemsRequest(
            user_id=user_id,
            agent_id=agent_id,
            scope=scope,
            categories=categories,
            source_clients=source_clients,
            raw_data_types=raw_data_types,
            time_range=time_range,
            metadata_filter=metadata_filter,
            limit=limit,
            cursor=cursor,
        )
        with OfficialMemindClient(
            base_url=self.base_url,
            api_token=self.token,
            timeout=self.timeout,
            max_retries=self.max_retries,
        ) as client:
            return client.memory.query_items(request)

    def query_raw_data(
        self,
        user_id,
        agent_id,
        types=None,
        source_clients=None,
        time_range=None,
        metadata_filter=None,
        include=None,
        limit=None,
        cursor=None,
    ):
        from memind import MemindClient as OfficialMemindClient
        from memind import QueryMemoryRawDataRequest, RawDataQueryIncludeOptions

        include_options = (
            RawDataQueryIncludeOptions(**include)
            if isinstance(include, dict)
            else include
        )
        request = QueryMemoryRawDataRequest(
            user_id=user_id,
            agent_id=agent_id,
            types=types,
            source_clients=source_clients,
            time_range=time_range,
            metadata_filter=metadata_filter,
            include=include_options,
            limit=limit,
            cursor=cursor,
        )
        with OfficialMemindClient(
            base_url=self.base_url,
            api_token=self.token,
            timeout=self.timeout,
            max_retries=self.max_retries,
        ) as client:
            return client.memory.query_raw_data(request)
