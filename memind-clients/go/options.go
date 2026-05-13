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
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

const (
	envBaseURL  = "MEMIND_BASE_URL"
	envAPIToken = "MEMIND_API_TOKEN"
)

var sdkOwnedHeaders = map[string]struct{}{
	"accept":        {},
	"content-type":  {},
	"authorization": {},
	"user-agent":    {},
}

type clientConfig struct {
	baseURL    string
	apiToken   string
	timeout    time.Duration
	maxRetries int
	httpClient *http.Client
	headers    http.Header
	userAgent  string
}

type requestConfig struct {
	timeoutSet    bool
	timeout       time.Duration
	maxRetriesSet bool
	maxRetries    int
	headers       http.Header
}

func WithBaseURL(baseURL string) Option {
	return func(cfg *clientConfig) error {
		cfg.baseURL = normalizeBaseURL(baseURL)
		return nil
	}
}

func WithAPIToken(token string) Option {
	return func(cfg *clientConfig) error {
		cfg.apiToken = strings.TrimSpace(token)
		return nil
	}
}

func WithHTTPClient(client *http.Client) Option {
	return func(cfg *clientConfig) error {
		if client == nil {
			return fmt.Errorf("http client must not be nil")
		}
		cfg.httpClient = client
		return nil
	}
}

func WithTimeout(timeout time.Duration) Option {
	return func(cfg *clientConfig) error {
		if timeout <= 0 {
			return fmt.Errorf("timeout must be greater than zero")
		}
		cfg.timeout = timeout
		return nil
	}
}

func WithMaxRetries(maxRetries int) Option {
	return func(cfg *clientConfig) error {
		if maxRetries < 0 {
			return fmt.Errorf("maxRetries must be non-negative")
		}
		cfg.maxRetries = maxRetries
		return nil
	}
}

func WithHeader(name, value string) Option {
	return func(cfg *clientConfig) error {
		if err := validateCustomHeader(name); err != nil {
			return err
		}
		cfg.headers.Set(name, value)
		return nil
	}
}

func WithUserAgent(userAgent string) Option {
	return func(cfg *clientConfig) error {
		userAgent = strings.TrimSpace(userAgent)
		if userAgent == "" {
			return fmt.Errorf("userAgent must not be blank")
		}
		cfg.userAgent = userAgent
		return nil
	}
}

type RequestOption func(*requestConfig) error

func WithRequestTimeout(timeout time.Duration) RequestOption {
	return func(cfg *requestConfig) error {
		if timeout <= 0 {
			return fmt.Errorf("request timeout must be greater than zero")
		}
		cfg.timeoutSet = true
		cfg.timeout = timeout
		return nil
	}
}

func WithRequestMaxRetries(maxRetries int) RequestOption {
	return func(cfg *requestConfig) error {
		if maxRetries < 0 {
			return fmt.Errorf("request maxRetries must be non-negative")
		}
		cfg.maxRetriesSet = true
		cfg.maxRetries = maxRetries
		return nil
	}
}

func WithRequestHeader(name, value string) RequestOption {
	return func(cfg *requestConfig) error {
		if err := validateCustomHeader(name); err != nil {
			return err
		}
		if cfg.headers == nil {
			cfg.headers = make(http.Header)
		}
		cfg.headers.Set(name, value)
		return nil
	}
}

func defaultClientConfig() clientConfig {
	return clientConfig{
		baseURL:    normalizeBaseURL(os.Getenv(envBaseURL)),
		apiToken:   strings.TrimSpace(os.Getenv(envAPIToken)),
		timeout:    30 * time.Second,
		maxRetries: 2,
		httpClient: http.DefaultClient,
		headers:    make(http.Header),
		userAgent:  "memind-go/" + Version,
	}
}

func normalizeBaseURL(baseURL string) string {
	return strings.TrimRight(strings.TrimSpace(baseURL), "/")
}

func validateBaseURL(baseURL string) error {
	parsed, err := url.Parse(baseURL)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return fmt.Errorf("baseURL must be an absolute URL with http or https scheme")
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return fmt.Errorf("baseURL scheme must be http or https")
	}
	if parsed.RawQuery != "" || parsed.Fragment != "" {
		return fmt.Errorf("baseURL must not include query or fragment")
	}
	return nil
}

func validateCustomHeader(name string) error {
	normalized := strings.ToLower(strings.TrimSpace(name))
	if normalized == "" {
		return fmt.Errorf("header name must not be blank")
	}
	if _, ok := sdkOwnedHeaders[normalized]; ok {
		return fmt.Errorf("header %q is managed by the SDK", name)
	}
	return nil
}
