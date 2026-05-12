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

from __future__ import annotations

import os
from uuid import uuid4

import pytest

from memind import ConversationContent, MemindAPIError, MemindClient, Message, Strategy

pytestmark = pytest.mark.skipif(
    os.getenv("MEMIND_INTEGRATION_TEST", "").lower() != "true",
    reason="Set MEMIND_INTEGRATION_TEST=true to run real-server integration tests",
)


def test_client_can_call_real_memind_server_health_and_error_envelope() -> None:
    base_url = os.getenv("MEMIND_BASE_URL", "http://localhost:8366")
    api_token = os.getenv("MEMIND_API_TOKEN")
    suffix = uuid4().hex

    with MemindClient(base_url=base_url, api_token=api_token, timeout=60.0) as client:
        health = client.health()
        assert health.status.upper() == "UP"

        with pytest.raises(MemindAPIError) as exc_info:
            client.memory.retrieve(
                user_id=f"python-it-user-{suffix}",
                agent_id=f"python-it-agent-{suffix}",
                query="",
                strategy=Strategy.SIMPLE,
            )

        assert exc_info.value.status_code == 400
        assert exc_info.value.error_code == "validation_failed"
        assert exc_info.value.body is not None
        assert "error" in exc_info.value.body


@pytest.mark.skipif(
    os.getenv("MEMIND_FULL_MEMORY_FLOW_TEST", "").lower() != "true",
    reason="Set MEMIND_FULL_MEMORY_FLOW_TEST=true to run extraction/retrieval integration tests",
)
def test_client_can_run_real_memind_server_memory_flow() -> None:
    base_url = os.getenv("MEMIND_BASE_URL", "http://localhost:8366")
    api_token = os.getenv("MEMIND_API_TOKEN")
    suffix = uuid4().hex
    user_id = f"python-it-user-{suffix}"
    agent_id = f"python-it-agent-{suffix}"
    memory_text = f"Python integration memory {suffix}"

    with MemindClient(base_url=base_url, api_token=api_token, timeout=60.0) as client:
        health = client.health()
        assert health.status.upper() == "UP"

        extract = client.memory.extract(
            user_id=user_id,
            agent_id=agent_id,
            raw_content=ConversationContent(messages=[Message.user(memory_text)]),
            source_client="memind-python-integration-test",
        )
        assert extract.status in {"SUCCESS", "PARTIAL_SUCCESS"}
        assert extract.raw_data_ids is not None

        retrieved = client.memory.retrieve(
            user_id=user_id,
            agent_id=agent_id,
            query=memory_text,
            strategy=Strategy.SIMPLE,
            trace=True,
        )
        assert retrieved.items
        assert any(item.text for item in retrieved.items)
