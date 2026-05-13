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

use serde::{Deserialize, Deserializer, Serialize, Serializer};

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Role {
    #[serde(rename = "USER")]
    User,
    #[serde(rename = "ASSISTANT")]
    Assistant,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum Strategy {
    #[serde(rename = "SIMPLE")]
    Simple,
    #[serde(rename = "DEEP")]
    Deep,
}

#[derive(Clone, Debug, PartialEq, Eq)]
#[non_exhaustive]
pub enum ExtractStatus {
    Success,
    PartialSuccess,
    Failed,
    Unknown(String),
}

impl Serialize for ExtractStatus {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        match self {
            Self::Success => serializer.serialize_str("SUCCESS"),
            Self::PartialSuccess => serializer.serialize_str("PARTIAL_SUCCESS"),
            Self::Failed => serializer.serialize_str("FAILED"),
            Self::Unknown(value) => serializer.serialize_str(value),
        }
    }
}

impl<'de> Deserialize<'de> for ExtractStatus {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value = String::deserialize(deserializer)?;
        Ok(match value.as_str() {
            "SUCCESS" => Self::Success,
            "PARTIAL_SUCCESS" => Self::PartialSuccess,
            "FAILED" => Self::Failed,
            _ => Self::Unknown(value),
        })
    }
}
