// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use serde::de;
use serde::ser::SerializeMap;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use time::OffsetDateTime;

use super::Role;
use crate::{MemindError, Result};

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Message {
    pub role: Role,
    pub content: Vec<ContentBlock>,
    #[serde(
        default,
        with = "time::serde::rfc3339::option",
        skip_serializing_if = "Option::is_none"
    )]
    pub timestamp: Option<OffsetDateTime>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub user_name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source_client: Option<String>,
}

impl Message {
    pub fn user(text: impl Into<String>) -> Self {
        Self {
            role: Role::User,
            content: vec![ContentBlock::text(text)],
            timestamp: None,
            user_name: None,
            source_client: None,
        }
    }

    pub fn assistant(text: impl Into<String>) -> Self {
        Self {
            role: Role::Assistant,
            content: vec![ContentBlock::text(text)],
            timestamp: None,
            user_name: None,
            source_client: None,
        }
    }

    pub fn with_timestamp(mut self, timestamp: OffsetDateTime) -> Self {
        self.timestamp = Some(timestamp);
        self
    }

    pub fn with_user_name(mut self, user_name: impl Into<String>) -> Self {
        let trimmed = user_name.into().trim().to_string();
        self.user_name = (!trimmed.is_empty()).then_some(trimmed);
        self
    }

    pub fn with_source_client(mut self, source_client: impl Into<String>) -> Self {
        let trimmed = source_client.into().trim().to_string();
        self.source_client = (!trimmed.is_empty()).then_some(trimmed);
        self
    }

    pub(crate) fn validate(&self) -> Result<()> {
        if self.content.is_empty() {
            return Err(MemindError::invalid_request(
                "message.content must contain at least one block",
            ));
        }
        for block in &self.content {
            block.validate()?;
        }
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum ContentBlock {
    Text { text: String },
    Image { source: Source },
    Audio { source: Source },
    Video { source: Source },
}

impl ContentBlock {
    pub fn text(text: impl Into<String>) -> Self {
        Self::Text { text: text.into() }
    }

    pub fn image(source: Source) -> Self {
        Self::Image { source }
    }

    pub fn audio(source: Source) -> Self {
        Self::Audio { source }
    }

    pub fn video(source: Source) -> Self {
        Self::Video { source }
    }

    pub(crate) fn validate(&self) -> Result<()> {
        match self {
            Self::Text { .. } => Ok(()),
            Self::Image { source } | Self::Audio { source } | Self::Video { source } => {
                source.validate()
            }
        }
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum Source {
    Url {
        url: String,
    },
    Base64 {
        #[serde(rename = "media_type")]
        media_type: String,
        data: String,
    },
}

impl Source {
    pub fn url(url: impl Into<String>) -> Self {
        Self::Url { url: url.into() }
    }

    pub fn base64(media_type: impl Into<String>, data: impl Into<String>) -> Self {
        Self::Base64 {
            media_type: media_type.into(),
            data: data.into(),
        }
    }

    pub(crate) fn validate(&self) -> Result<()> {
        match self {
            Self::Url { url } if url.trim().is_empty() => Err(MemindError::invalid_request(
                "Source::Url requires a non-blank URL",
            )),
            Self::Base64 { media_type, .. } if media_type.trim().is_empty() => Err(
                MemindError::invalid_request("Source::Base64 requires a non-blank media_type"),
            ),
            Self::Base64 { data, .. } if data.is_empty() => Err(MemindError::invalid_request(
                "Source::Base64 requires non-empty data",
            )),
            _ => Ok(()),
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub enum RawContent {
    Conversation {
        messages: Vec<Message>,
    },
    Object {
        content_type: String,
        fields: serde_json::Map<String, serde_json::Value>,
    },
}

impl RawContent {
    pub fn conversation(messages: Vec<Message>) -> Result<Self> {
        let raw = Self::Conversation { messages };
        raw.validate()?;
        Ok(raw)
    }

    pub fn object(
        content_type: impl Into<String>,
        fields: serde_json::Map<String, serde_json::Value>,
    ) -> Result<Self> {
        let content_type = content_type.into().trim().to_string();
        if content_type.is_empty() {
            return Err(MemindError::invalid_request(
                "RawContent::object requires a non-blank content_type",
            ));
        }
        if content_type == "conversation" {
            return Err(MemindError::invalid_request(
                "RawContent::object cannot use content_type \"conversation\"; use RawContent::conversation",
            ));
        }
        if fields.contains_key("type") {
            return Err(MemindError::invalid_request(
                "RawContent::object fields must not contain \"type\"",
            ));
        }
        Ok(Self::Object {
            content_type,
            fields,
        })
    }

    pub(crate) fn validate(&self) -> Result<()> {
        match self {
            Self::Conversation { messages } => {
                if messages.is_empty() {
                    return Err(MemindError::invalid_request(
                        "conversation raw content requires at least one message",
                    ));
                }
                for message in messages {
                    message.validate()?;
                }
                Ok(())
            }
            Self::Object {
                content_type,
                fields,
            } => {
                if content_type.trim().is_empty() {
                    return Err(MemindError::invalid_request(
                        "RawContent::object requires a non-blank content_type",
                    ));
                }
                if content_type == "conversation" {
                    return Err(MemindError::invalid_request(
                        "RawContent::object cannot use content_type \"conversation\"; use RawContent::conversation",
                    ));
                }
                if fields.contains_key("type") {
                    return Err(MemindError::invalid_request(
                        "RawContent::object fields must not contain \"type\"",
                    ));
                }
                Ok(())
            }
        }
    }
}

impl Serialize for RawContent {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        match self {
            Self::Conversation { messages } => {
                let mut map = serializer.serialize_map(Some(2))?;
                map.serialize_entry("type", "conversation")?;
                map.serialize_entry("messages", messages)?;
                map.end()
            }
            Self::Object {
                content_type,
                fields,
            } => {
                let mut map = serializer.serialize_map(Some(fields.len() + 1))?;
                map.serialize_entry("type", content_type)?;
                for (key, value) in fields {
                    map.serialize_entry(key, value)?;
                }
                map.end()
            }
        }
    }
}

impl<'de> Deserialize<'de> for RawContent {
    fn deserialize<D>(deserializer: D) -> std::result::Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value = serde_json::Value::deserialize(deserializer)?;
        let mut object = match value {
            serde_json::Value::Object(object) => object,
            _ => return Err(de::Error::custom("raw content must be a JSON object")),
        };

        let content_type = object
            .remove("type")
            .and_then(|value| value.as_str().map(ToOwned::to_owned))
            .ok_or_else(|| de::Error::custom("raw content requires string field \"type\""))?;

        if content_type == "conversation" {
            let messages_value = object
                .remove("messages")
                .ok_or_else(|| de::Error::custom("conversation raw content requires messages"))?;
            let messages =
                Vec::<Message>::deserialize(messages_value).map_err(de::Error::custom)?;
            return Ok(Self::Conversation { messages });
        }

        Ok(Self::Object {
            content_type,
            fields: object,
        })
    }
}
