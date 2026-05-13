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

#![forbid(unsafe_code)]

mod client;
mod config;
mod error;
mod http;
mod retry;

pub mod models;
pub mod resources;

pub use client::{ClientBuilder, MemindClient};
pub use config::RequestOptions;
pub use error::{MemindApiError, MemindError, Result};
pub use models::{
    AddMessageRequest, AddMessageResponse, CommitMemoryRequest, ContentBlock, ExtractMemoryRequest,
    ExtractMemoryResponse, ExtractStatus, FinalView, HealthResponse, MergeView, Message,
    RawContent, RetrievalTraceView, RetrieveMemoryRequest, RetrieveMemoryResponse,
    RetrievedInsight, RetrievedItem, RetrievedRawData, Role, Source, StageView, Strategy,
};
pub use resources::MemoryClient;
