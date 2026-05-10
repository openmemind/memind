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

from typing import Annotated, Any, Literal

from pydantic import Field, model_serializer, model_validator

from memind._models import MemindModel
from memind.types.common import Role


class UrlSource(MemindModel):
    type: Literal["url"] = "url"
    url: str


class Base64Source(MemindModel):
    type: Literal["base64"] = "base64"
    media_type: str = Field(alias="media_type")
    data: str


Source = Annotated[UrlSource | Base64Source, Field(discriminator="type")]


class TextBlock(MemindModel):
    type: Literal["text"] = "text"
    text: str


class ImageBlock(MemindModel):
    type: Literal["image"] = "image"
    source: Source


class AudioBlock(MemindModel):
    type: Literal["audio"] = "audio"
    source: Source


class VideoBlock(MemindModel):
    type: Literal["video"] = "video"
    source: Source


ContentBlock = Annotated[
    TextBlock | ImageBlock | AudioBlock | VideoBlock,
    Field(discriminator="type"),
]


class Message(MemindModel):
    role: Role
    content: list[ContentBlock]
    timestamp: str | None = None
    user_name: str | None = None
    source_client: str | None = None

    @classmethod
    def user(cls, text: str, *, timestamp: str | None = None) -> Message:
        return cls(role=Role.USER, content=[TextBlock(text=text)], timestamp=timestamp)

    @classmethod
    def assistant(cls, text: str, *, timestamp: str | None = None) -> Message:
        return cls(role=Role.ASSISTANT, content=[TextBlock(text=text)], timestamp=timestamp)


class RawContent(MemindModel):
    type: str


class ConversationContent(RawContent):
    type: Literal["conversation"] = "conversation"
    messages: list[Message]


class MapRawContent(RawContent):
    type: str
    properties: dict[str, Any] = Field(default_factory=dict)

    @model_serializer
    def _serialize(self) -> dict[str, Any]:
        result: dict[str, Any] = {"type": self.type}
        result.update(self.properties)
        return result

    @model_validator(mode="before")
    @classmethod
    def _parse_flat_fields(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "properties" in data:
                return data
            type_val = data.get("type", "")
            properties = {key: value for key, value in data.items() if key != "type"}
            return {"type": type_val, "properties": properties}
        return data


RawContentValue = ConversationContent | MapRawContent
