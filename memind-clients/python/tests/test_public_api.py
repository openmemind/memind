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

import memind
from memind import (
    AsyncMemindClient,
    ConversationContent,
    MemindAPIError,
    MemindClient,
    MemindError,
    Message,
    MetadataCondition,
    MetadataFilter,
    QueryMemoryItemsRequest,
    QueryMemoryRawDataRequest,
    RawContentValue,
    Strategy,
    TimeRange,
)


def test_public_exports() -> None:
    assert memind.__version__ == "0.2.0"
    assert MemindClient is not None
    assert AsyncMemindClient is not None
    assert MemindError is not None
    assert MemindAPIError is not None
    assert RawContentValue is not None
    assert Message.user("hello").role.value == "USER"
    assert Strategy.SIMPLE.value == "SIMPLE"
    assert ConversationContent(messages=[Message.user("hi")]).type == "conversation"
    assert MetadataFilter(all=[MetadataCondition(path="project", op="eq", value="memind")])
    assert QueryMemoryItemsRequest(user_id="u1", agent_id="a1")
    assert QueryMemoryRawDataRequest(user_id="u1", agent_id="a1")
    assert TimeRange(field="occurredAt")
