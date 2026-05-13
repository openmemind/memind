/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package memind

import (
	"context"
	"net/http"
)

type MemoryService struct {
	client *Client
}

func (s *MemoryService) Extract(ctx context.Context, req ExtractMemoryRequest, opts ...RequestOption) (*ExtractMemoryResponse, error) {
	if err := validateExtractRequest(req); err != nil {
		return nil, err
	}
	cfg, err := applyRequestOptions(opts)
	if err != nil {
		return nil, err
	}
	if !cfg.maxRetriesSet {
		cfg.maxRetriesSet = true
		cfg.maxRetries = 0
	}
	var out ExtractMemoryResponse
	if err := s.client.do(ctx, http.MethodPost, "/memory/sync/extract", req, &out, cfg); err != nil {
		return nil, err
	}
	return &out, nil
}

func (s *MemoryService) AddMessage(ctx context.Context, req AddMessageRequest, opts ...RequestOption) (*AddMessageResponse, error) {
	if err := validateAddMessageRequest(req); err != nil {
		return nil, err
	}
	cfg, err := applyRequestOptions(opts)
	if err != nil {
		return nil, err
	}
	if !cfg.maxRetriesSet {
		cfg.maxRetriesSet = true
		cfg.maxRetries = 0
	}
	var out AddMessageResponse
	if err := s.client.do(ctx, http.MethodPost, "/memory/sync/add-message", req, &out, cfg); err != nil {
		return nil, err
	}
	return &out, nil
}

func (s *MemoryService) Commit(ctx context.Context, req CommitMemoryRequest, opts ...RequestOption) (*ExtractMemoryResponse, error) {
	if err := validateCommitRequest(req); err != nil {
		return nil, err
	}
	cfg, err := applyRequestOptions(opts)
	if err != nil {
		return nil, err
	}
	if !cfg.maxRetriesSet {
		cfg.maxRetriesSet = true
		cfg.maxRetries = 0
	}
	var out ExtractMemoryResponse
	if err := s.client.do(ctx, http.MethodPost, "/memory/sync/commit", req, &out, cfg); err != nil {
		return nil, err
	}
	return &out, nil
}

func (s *MemoryService) Retrieve(ctx context.Context, req RetrieveMemoryRequest, opts ...RequestOption) (*RetrieveMemoryResponse, error) {
	if err := validateRetrieveRequest(req); err != nil {
		return nil, err
	}
	cfg, err := applyRequestOptions(opts)
	if err != nil {
		return nil, err
	}
	var out RetrieveMemoryResponse
	if err := s.client.do(ctx, http.MethodPost, "/memory/retrieve", req, &out, cfg); err != nil {
		return nil, err
	}
	return &out, nil
}

func (s *MemoryService) EnqueueExtract(ctx context.Context, req ExtractMemoryRequest, opts ...RequestOption) (*OperationAccepted, error) {
	if err := validateExtractRequest(req); err != nil {
		return nil, err
	}
	return s.enqueue(ctx, "/memory/async/extract", req, opts...)
}

func (s *MemoryService) EnqueueAddMessage(ctx context.Context, req AddMessageRequest, opts ...RequestOption) (*OperationAccepted, error) {
	if err := validateAddMessageRequest(req); err != nil {
		return nil, err
	}
	return s.enqueue(ctx, "/memory/async/add-message", req, opts...)
}

func (s *MemoryService) EnqueueCommit(ctx context.Context, req CommitMemoryRequest, opts ...RequestOption) (*OperationAccepted, error) {
	if err := validateCommitRequest(req); err != nil {
		return nil, err
	}
	return s.enqueue(ctx, "/memory/async/commit", req, opts...)
}

func (s *MemoryService) enqueue(ctx context.Context, path string, req any, opts ...RequestOption) (*OperationAccepted, error) {
	cfg, err := applyRequestOptions(opts)
	if err != nil {
		return nil, err
	}
	if !cfg.maxRetriesSet {
		cfg.maxRetriesSet = true
		cfg.maxRetries = 0
	}
	var out OperationAccepted
	if err := s.client.do(ctx, http.MethodPost, path, req, &out, cfg); err != nil {
		return nil, err
	}
	return &out, nil
}

func applyRequestOptions(opts []RequestOption) (requestConfig, error) {
	var cfg requestConfig
	for _, opt := range opts {
		if opt == nil {
			continue
		}
		if err := opt(&cfg); err != nil {
			return requestConfig{}, err
		}
	}
	return cfg, nil
}
