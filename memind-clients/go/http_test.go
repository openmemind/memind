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
	"bytes"
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (fn roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return fn(req)
}

type closeTrackingBody struct {
	*bytes.Reader
	closed *atomic.Bool
}

func (b closeTrackingBody) Close() error {
	b.closed.Store(true)
	return nil
}

func TestDoSetsHeadersAndParsesDataEnvelope(t *testing.T) {
	var gotAuth string
	var gotUA string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		gotUA = r.Header.Get("User-Agent")
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":{"status":"UP","service":"memind-server"}}`))
	}))
	defer server.Close()

	client, err := NewClient(
		WithBaseURL(server.URL),
		WithAPIToken("token"),
		WithHeader("X-Test", "true"),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}

	var out HealthResponse
	if err := client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{}); err != nil {
		t.Fatalf("do error = %v", err)
	}
	if out.Status != "UP" || out.Service != "memind-server" {
		t.Fatalf("out = %#v", out)
	}
	if gotAuth != "Bearer token" {
		t.Fatalf("Authorization = %q", gotAuth)
	}
	if !strings.HasPrefix(gotUA, "memind-go/") {
		t.Fatalf("User-Agent = %q", gotUA)
	}
}

func TestDoRetriesPostWithFullBody(t *testing.T) {
	var attempts atomic.Int32
	var bodies []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts.Add(1)
		body, _ := io.ReadAll(r.Body)
		bodies = append(bodies, string(body))
		w.Header().Set("Content-Type", "application/json")
		if attempts.Load() == 1 {
			w.WriteHeader(http.StatusServiceUnavailable)
			_, _ = w.Write([]byte(`{"error":{"code":"unavailable","message":"retry"}}`))
			return
		}
		_, _ = w.Write([]byte(`{"data":{"status":"UP","service":"memind-server"}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	cfg := requestConfig{maxRetriesSet: true, maxRetries: 1}
	var out HealthResponse
	if err := client.do(context.Background(), http.MethodPost, "/health", map[string]string{"a": "b"}, &out, cfg); err != nil {
		t.Fatalf("do error = %v", err)
	}
	if len(bodies) != 2 || bodies[0] != `{"a":"b"}` || bodies[1] != `{"a":"b"}` {
		t.Fatalf("bodies = %#v", bodies)
	}
}

func TestDoMapsErrors(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("X-Request-Id", "req-1")
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"error":{"code":"unauthorized","message":"bad token","details":{"reason":"missing"}}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out HealthResponse
	err = client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{})
	var authErr *AuthenticationError
	if !errors.As(err, &authErr) {
		t.Fatalf("error = %T, want *AuthenticationError", err)
	}
	if authErr.RequestID != "req-1" || authErr.ErrorCode != "unauthorized" {
		t.Fatalf("authErr = %#v", authErr.APIError)
	}
}

func TestDoMapsRateLimitAndRetryAfter(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Retry-After", "3")
		w.WriteHeader(http.StatusTooManyRequests)
		_, _ = w.Write([]byte(`{"error":{"code":"rate_limited","message":"slow down"}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out HealthResponse
	err = client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{})
	var rateErr *RateLimitError
	if !errors.As(err, &rateErr) {
		t.Fatalf("error = %T, want *RateLimitError", err)
	}
	if rateErr.ErrorCode != "rate_limited" {
		t.Fatalf("ErrorCode = %q, want rate_limited", rateErr.ErrorCode)
	}
	if rateErr.RetryAfter == nil || *rateErr.RetryAfter != 3*time.Second {
		t.Fatalf("RetryAfter = %v, want 3s", rateErr.RetryAfter)
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("error = %T, want *APIError chain", err)
	}
}

func TestDoRejectsInvalidSuccessEnvelope(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"ok":true}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out HealthResponse
	err = client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{})
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.ErrorCode != "parse_error" {
		t.Fatalf("error = %#v, want parse APIError", err)
	}
}

func TestDoContextCancellationReturnsPromptly(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(200 * time.Millisecond)
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	var out HealthResponse
	err = client.do(ctx, http.MethodGet, "/health", nil, &out, requestConfig{})
	if err == nil {
		t.Fatal("do error = nil, want cancellation error")
	}
}

func TestDoClosesResponseBody(t *testing.T) {
	var closed atomic.Bool
	httpClient := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			body := closeTrackingBody{
				Reader: bytes.NewReader([]byte(`{"data":{"status":"UP","service":"memind-server"}}`)),
				closed: &closed,
			}
			return &http.Response{
				StatusCode: http.StatusOK,
				Status:     "200 OK",
				Header:     http.Header{"Content-Type": []string{"application/json"}},
				Body:       body,
				Request:    req,
			}, nil
		}),
	}
	client, err := NewClient(
		WithBaseURL("http://memind.test"),
		WithHTTPClient(httpClient),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out HealthResponse
	if err := client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{}); err != nil {
		t.Fatalf("do error = %v", err)
	}
	if !closed.Load() {
		t.Fatal("response body was not closed")
	}
}

func TestDoClosesAPIErrorResponseBody(t *testing.T) {
	var closed atomic.Bool
	httpClient := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			body := closeTrackingBody{
				Reader: bytes.NewReader([]byte(`{"error":{"code":"unauthorized","message":"bad token"}}`)),
				closed: &closed,
			}
			return &http.Response{
				StatusCode: http.StatusUnauthorized,
				Status:     "401 Unauthorized",
				Header:     http.Header{"Content-Type": []string{"application/json"}},
				Body:       body,
				Request:    req,
			}, nil
		}),
	}
	client, err := NewClient(
		WithBaseURL("http://memind.test"),
		WithHTTPClient(httpClient),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out HealthResponse
	err = client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{})
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("error = %T, want *APIError", err)
	}
	if !closed.Load() {
		t.Fatal("API error response body was not closed")
	}
}

func TestDoClosesParseErrorResponseBody(t *testing.T) {
	var closed atomic.Bool
	httpClient := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			body := closeTrackingBody{
				Reader: bytes.NewReader([]byte(`not-json`)),
				closed: &closed,
			}
			return &http.Response{
				StatusCode: http.StatusOK,
				Status:     "200 OK",
				Header:     http.Header{"Content-Type": []string{"text/plain"}},
				Body:       body,
				Request:    req,
			}, nil
		}),
	}
	client, err := NewClient(
		WithBaseURL("http://memind.test"),
		WithHTTPClient(httpClient),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out HealthResponse
	err = client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{})
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.ErrorCode != "parse_error" {
		t.Fatalf("error = %#v, want parse APIError", err)
	}
	if !closed.Load() {
		t.Fatal("parse error response body was not closed")
	}
}

func TestDoClosesRetryResponseBodies(t *testing.T) {
	var attempts atomic.Int32
	var firstClosed atomic.Bool
	var secondClosed atomic.Bool
	httpClient := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			attempt := attempts.Add(1)
			if attempt == 1 {
				body := closeTrackingBody{
					Reader: bytes.NewReader([]byte(`{"error":{"code":"unavailable","message":"retry"}}`)),
					closed: &firstClosed,
				}
				return &http.Response{
					StatusCode: http.StatusServiceUnavailable,
					Status:     "503 Service Unavailable",
					Header: http.Header{
						"Content-Type": []string{"application/json"},
						"Retry-After":  []string{"0"},
					},
					Body:    body,
					Request: req,
				}, nil
			}
			body := closeTrackingBody{
				Reader: bytes.NewReader([]byte(`{"data":{"status":"UP","service":"memind-server"}}`)),
				closed: &secondClosed,
			}
			return &http.Response{
				StatusCode: http.StatusOK,
				Status:     "200 OK",
				Header:     http.Header{"Content-Type": []string{"application/json"}},
				Body:       body,
				Request:    req,
			}, nil
		}),
	}
	client, err := NewClient(
		WithBaseURL("http://memind.test"),
		WithHTTPClient(httpClient),
	)
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	cfg := requestConfig{maxRetriesSet: true, maxRetries: 1}
	var out HealthResponse
	if err := client.do(context.Background(), http.MethodGet, "/health", nil, &out, cfg); err != nil {
		t.Fatalf("do error = %v", err)
	}
	if attempts.Load() != 2 {
		t.Fatalf("attempts = %d, want 2", attempts.Load())
	}
	if !firstClosed.Load() || !secondClosed.Load() {
		t.Fatalf("retry response bodies closed = %v, %v; want both true", firstClosed.Load(), secondClosed.Load())
	}
}

func TestMissingRequiredArraysDecodeToEmptySlices(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":{"status":"SUCCESS"}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out ExtractMemoryResponse
	if err := client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{}); err != nil {
		t.Fatalf("do error = %v", err)
	}
	if out.RawDataIDs == nil || out.ItemIDs == nil || out.InsightIDs == nil {
		t.Fatalf("arrays were not normalized: %#v", out)
	}
}

func TestNullRequiredArraysReturnParseError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":{"items":null,"insights":[],"rawData":[],"evidences":[]}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out RetrieveMemoryResponse
	err = client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{})
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.ErrorCode != "parse_error" {
		t.Fatalf("error = %#v, want parse APIError", err)
	}
}

func TestTraceResponsesDecodeTypedViews(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":{"items":[],"insights":[],"rawData":[],"evidences":[],"trace":{"stages":[{"stage":"vector","degraded":false,"skipped":false}],"merge":{"inputCount":2,"outputCount":1,"deduplicatedCount":1,"sourceCount":1,"status":"OK"},"finalResults":{"strategy":"SIMPLE","status":"OK","itemCount":1,"insightCount":0,"rawDataCount":1,"evidenceCount":1}}}}`))
	}))
	defer server.Close()

	client, err := NewClient(WithBaseURL(server.URL))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	var out RetrieveMemoryResponse
	if err := client.do(context.Background(), http.MethodGet, "/health", nil, &out, requestConfig{}); err != nil {
		t.Fatalf("do error = %v", err)
	}
	if out.Trace == nil || len(out.Trace.Stages) != 1 || out.Trace.Merge == nil || out.Trace.FinalResults == nil {
		t.Fatalf("trace = %#v", out.Trace)
	}
	if out.Trace.Merge.DeduplicatedCount != 1 || out.Trace.FinalResults.EvidenceCount != 1 {
		t.Fatalf("trace values = %#v", out.Trace)
	}
}
