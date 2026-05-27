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

use std::sync::Arc;

use crate::client::ClientInner;
use crate::models::{
    AddMessageRequest, AddMessageResponse, CommitMemoryRequest, ExtractMemoryRequest,
    ExtractMemoryResponse, QueryMemoryItemsRequest, QueryMemoryItemsResponse,
    QueryMemoryRawDataRequest, QueryMemoryRawDataResponse, RetrieveMemoryRequest,
    RetrieveMemoryResponse,
};
use crate::{http, RequestOptions, Result};

#[derive(Clone, Debug)]
pub struct MemoryClient {
    pub(crate) inner: Arc<ClientInner>,
}

impl MemoryClient {
    pub(crate) fn new(inner: Arc<ClientInner>) -> Self {
        Self { inner }
    }

    pub async fn extract(&self, request: ExtractMemoryRequest) -> Result<ExtractMemoryResponse> {
        self.extract_with_options(request, RequestOptions::new())
            .await
    }

    pub async fn extract_with_options(
        &self,
        request: ExtractMemoryRequest,
        options: RequestOptions,
    ) -> Result<ExtractMemoryResponse> {
        request.validate()?;
        http::post_json(&self.inner, "/memory/sync/extract", &request, options, 0).await
    }

    pub async fn add_message(&self, request: AddMessageRequest) -> Result<AddMessageResponse> {
        self.add_message_with_options(request, RequestOptions::new())
            .await
    }

    pub async fn add_message_with_options(
        &self,
        request: AddMessageRequest,
        options: RequestOptions,
    ) -> Result<AddMessageResponse> {
        request.validate()?;
        http::post_json(
            &self.inner,
            "/memory/sync/add-message",
            &request,
            options,
            0,
        )
        .await
    }

    pub async fn commit(&self, request: CommitMemoryRequest) -> Result<ExtractMemoryResponse> {
        self.commit_with_options(request, RequestOptions::new())
            .await
    }

    pub async fn commit_with_options(
        &self,
        request: CommitMemoryRequest,
        options: RequestOptions,
    ) -> Result<ExtractMemoryResponse> {
        request.validate()?;
        http::post_json(&self.inner, "/memory/sync/commit", &request, options, 0).await
    }

    pub async fn retrieve(&self, request: RetrieveMemoryRequest) -> Result<RetrieveMemoryResponse> {
        self.retrieve_with_options(request, RequestOptions::new())
            .await
    }

    pub async fn retrieve_with_options(
        &self,
        request: RetrieveMemoryRequest,
        options: RequestOptions,
    ) -> Result<RetrieveMemoryResponse> {
        request.validate()?;
        http::post_json(
            &self.inner,
            "/memory/retrieve",
            &request,
            options,
            self.inner.config.max_retries,
        )
        .await
    }

    pub async fn query_items(
        &self,
        request: QueryMemoryItemsRequest,
    ) -> Result<QueryMemoryItemsResponse> {
        self.query_items_with_options(request, RequestOptions::new())
            .await
    }

    pub async fn query_items_with_options(
        &self,
        request: QueryMemoryItemsRequest,
        options: RequestOptions,
    ) -> Result<QueryMemoryItemsResponse> {
        request.validate()?;
        http::post_json(
            &self.inner,
            "/memory/items/query",
            &request,
            options,
            self.inner.config.max_retries,
        )
        .await
    }

    pub async fn query_raw_data(
        &self,
        request: QueryMemoryRawDataRequest,
    ) -> Result<QueryMemoryRawDataResponse> {
        self.query_raw_data_with_options(request, RequestOptions::new())
            .await
    }

    pub async fn query_raw_data_with_options(
        &self,
        request: QueryMemoryRawDataRequest,
        options: RequestOptions,
    ) -> Result<QueryMemoryRawDataResponse> {
        request.validate()?;
        http::post_json(
            &self.inner,
            "/memory/raw-data/query",
            &request,
            options,
            self.inner.config.max_retries,
        )
        .await
    }
}
