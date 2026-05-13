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
	"fmt"
	"net/http"
)

type Client struct {
	Memory *MemoryService

	config clientConfig
}

type Option func(*clientConfig) error

func NewClient(opts ...Option) (*Client, error) {
	cfg := defaultClientConfig()
	for _, opt := range opts {
		if opt == nil {
			continue
		}
		if err := opt(&cfg); err != nil {
			return nil, err
		}
	}
	if cfg.baseURL == "" {
		return nil, fmt.Errorf("baseURL is required; pass WithBaseURL or set MEMIND_BASE_URL")
	}
	if err := validateBaseURL(cfg.baseURL); err != nil {
		return nil, err
	}
	client := &Client{config: cfg}
	client.Memory = &MemoryService{client: client}
	return client, nil
}

func (c *Client) Health(ctx context.Context, opts ...RequestOption) (*HealthResponse, error) {
	cfg, err := applyRequestOptions(opts)
	if err != nil {
		return nil, err
	}
	var out HealthResponse
	if err := c.do(ctx, http.MethodGet, "/health", nil, &out, cfg); err != nil {
		return nil, err
	}
	return &out, nil
}
