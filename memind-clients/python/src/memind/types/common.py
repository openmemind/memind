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

from enum import Enum
from typing import Any, Generic, TypeVar

from memind._models import MemindModel

T = TypeVar("T")


class Role(str, Enum):
    USER = "USER"
    ASSISTANT = "ASSISTANT"


class Strategy(str, Enum):
    SIMPLE = "SIMPLE"
    DEEP = "DEEP"


class ApiResult(MemindModel, Generic[T]):
    data: T | None = None
    error: ApiError | None = None

    def is_success(self) -> bool:
        return self.error is None


class ApiError(MemindModel):
    code: str
    message: str
    details: Any | None = None
