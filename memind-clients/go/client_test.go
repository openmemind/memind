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
	"net/http"
	"testing"
	"time"
)

func TestVersion(t *testing.T) {
	if Version != "0.2.0" {
		t.Fatalf("Version = %q, want 0.2.0", Version)
	}
}

func TestNewClientRequiresBaseURL(t *testing.T) {
	t.Setenv("MEMIND_BASE_URL", "")

	client, err := NewClient()
	if err == nil {
		t.Fatal("NewClient() error = nil, want error")
	}
	if client != nil {
		t.Fatalf("NewClient() client = %#v, want nil", client)
	}
}

func TestNewClientUsesEnvBaseURL(t *testing.T) {
	t.Setenv("MEMIND_BASE_URL", " http://localhost:8366/ ")

	client, err := NewClient()
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	if got := client.config.baseURL; got != "http://localhost:8366" {
		t.Fatalf("baseURL = %q, want normalized env URL", got)
	}
}

func TestInvalidBaseURLRejected(t *testing.T) {
	for _, baseURL := range []string{"localhost:8366", "ftp://localhost:8366", "http://"} {
		_, err := NewClient(WithBaseURL(baseURL))
		if err == nil {
			t.Fatalf("WithBaseURL(%q) error = nil, want invalid baseURL error", baseURL)
		}
	}
}

func TestConstructorOptionsOverrideEnv(t *testing.T) {
	t.Setenv("MEMIND_BASE_URL", "http://env.example")
	t.Setenv("MEMIND_API_TOKEN", "env-token")

	client, err := NewClient(
		WithBaseURL("http://explicit.example/"),
		WithAPIToken(" explicit-token "),
		WithTimeout(10*time.Second),
		WithMaxRetries(4),
		WithUserAgent("custom-agent"),
		WithHeader("X-Memind-Test", "true"),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}

	if client.config.baseURL != "http://explicit.example" {
		t.Fatalf("baseURL = %q", client.config.baseURL)
	}
	if client.config.apiToken != "explicit-token" {
		t.Fatalf("apiToken = %q", client.config.apiToken)
	}
	if client.config.timeout != 10*time.Second {
		t.Fatalf("timeout = %s", client.config.timeout)
	}
	if client.config.maxRetries != 4 {
		t.Fatalf("maxRetries = %d", client.config.maxRetries)
	}
	if client.config.userAgent != "custom-agent" {
		t.Fatalf("userAgent = %q", client.config.userAgent)
	}
	if client.config.headers.Get("X-Memind-Test") != "true" {
		t.Fatalf("custom header missing")
	}
}

func TestBlankAPITokenIsIgnored(t *testing.T) {
	client, err := NewClient(
		WithBaseURL("http://localhost:8366"),
		WithAPIToken("   "),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	if client.config.apiToken != "" {
		t.Fatalf("apiToken = %q, want empty", client.config.apiToken)
	}
}

func TestWithHTTPClientNilRejected(t *testing.T) {
	_, err := NewClient(
		WithBaseURL("http://localhost:8366"),
		WithHTTPClient(nil),
	)
	if err == nil {
		t.Fatal("NewClient() error = nil, want nil http client error")
	}
}

func TestTimeoutsRejectZeroAndNegative(t *testing.T) {
	for _, timeout := range []time.Duration{0, -time.Second} {
		_, err := NewClient(
			WithBaseURL("http://localhost:8366"),
			WithTimeout(timeout),
		)
		if err == nil {
			t.Fatalf("WithTimeout(%s) error = nil, want error", timeout)
		}

		var cfg requestConfig
		if err := WithRequestTimeout(timeout)(&cfg); err == nil {
			t.Fatalf("WithRequestTimeout(%s) error = nil, want error", timeout)
		}
	}
}

func TestNegativeRetriesRejected(t *testing.T) {
	_, err := NewClient(
		WithBaseURL("http://localhost:8366"),
		WithMaxRetries(-1),
	)
	if err == nil {
		t.Fatal("WithMaxRetries(-1) error = nil, want error")
	}

	var cfg requestConfig
	if err := WithRequestMaxRetries(-1)(&cfg); err == nil {
		t.Fatal("WithRequestMaxRetries(-1) error = nil, want error")
	}
}

func TestSDKOwnedHeadersRejected(t *testing.T) {
	for _, name := range []string{"Accept", "content-type", "AUTHORIZATION", "User-Agent"} {
		_, err := NewClient(
			WithBaseURL("http://localhost:8366"),
			WithHeader(name, "bad"),
		)
		if err == nil {
			t.Fatalf("WithHeader(%q) error = nil, want error", name)
		}

		var cfg requestConfig
		if err := WithRequestHeader(name, "bad")(&cfg); err == nil {
			t.Fatalf("WithRequestHeader(%q) error = nil, want error", name)
		}
	}
}

func TestDefaultHTTPClientAndMemoryService(t *testing.T) {
	client, err := NewClient(WithBaseURL("http://localhost:8366"))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	if client.config.httpClient != http.DefaultClient {
		t.Fatalf("httpClient = %#v, want http.DefaultClient", client.config.httpClient)
	}
	if client.Memory == nil || client.Memory.client != client {
		t.Fatalf("Memory service not wired")
	}
}
