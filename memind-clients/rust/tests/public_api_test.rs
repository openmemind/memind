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

use memind::{
    AddMessageRequest, AddMessageResponse, ClientBuilder, CommitMemoryRequest, ContentBlock,
    ExtractMemoryRequest, ExtractMemoryResponse, ExtractStatus, HealthResponse, MemindApiError,
    MemindClient, MemindError, MemoryClient, Message, MetadataCondition, MetadataFilter,
    QueryMemoryItemsRequest, QueryMemoryItemsResponse, QueryMemoryRawDataRequest,
    QueryMemoryRawDataResponse, RawContent, RawDataQueryIncludeOptions, RequestOptions,
    RetrieveIncludeOptions, RetrieveMemoryRequest, RetrieveMemoryResponse, RetrievedItem, Role,
    Source, Strategy, TimeRange,
};

#[test]
fn public_api_exports_expected_names() {
    fn assert_type<T>() {}

    assert_type::<MemindClient>();
    assert_type::<ClientBuilder>();
    assert_type::<MemoryClient>();
    assert_type::<RequestOptions>();
    assert_type::<MemindError>();
    assert_type::<MemindApiError>();
    assert_type::<Role>();
    assert_type::<Strategy>();
    assert_type::<ExtractStatus>();
    assert_type::<HealthResponse>();
    assert_type::<Message>();
    assert_type::<ContentBlock>();
    assert_type::<Source>();
    assert_type::<RawContent>();
    assert_type::<ExtractMemoryRequest>();
    assert_type::<ExtractMemoryResponse>();
    assert_type::<AddMessageRequest>();
    assert_type::<AddMessageResponse>();
    assert_type::<CommitMemoryRequest>();
    assert_type::<RetrieveMemoryRequest>();
    assert_type::<RetrieveMemoryResponse>();
    assert_type::<RetrievedItem>();
    assert_type::<MetadataCondition>();
    assert_type::<MetadataFilter>();
    assert_type::<TimeRange>();
    assert_type::<RetrieveIncludeOptions>();
    assert_type::<RawDataQueryIncludeOptions>();
    assert_type::<QueryMemoryItemsRequest>();
    assert_type::<QueryMemoryItemsResponse>();
    assert_type::<QueryMemoryRawDataRequest>();
    assert_type::<QueryMemoryRawDataResponse>();
}
