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
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type rawEnvelope struct {
	Data  *json.RawMessage `json:"data"`
	Error *apiErrorBody    `json:"error"`
}

type apiErrorBody struct {
	Code    string          `json:"code"`
	Message string          `json:"message"`
	Details json.RawMessage `json:"details,omitempty"`
}

func (c *Client) do(ctx context.Context, method, path string, body any, out any, opts requestConfig) error {
	if ctx == nil {
		return &ValidationError{Issues: []ValidationIssue{{Field: "context", Message: "context must not be nil"}}}
	}

	effectiveTimeout := c.config.timeout
	if opts.timeoutSet {
		effectiveTimeout = opts.timeout
	}
	effectiveRetries := c.config.maxRetries
	if opts.maxRetriesSet {
		effectiveRetries = opts.maxRetries
	}

	var bodyBytes []byte
	var err error
	if body != nil {
		bodyBytes, err = json.Marshal(body)
		if err != nil {
			return &ConnectionError{Op: method, URL: c.url(path), Err: fmt.Errorf("marshal request body: %w", err)}
		}
	}

	var lastErr error
	for attempt := 0; attempt <= effectiveRetries; attempt++ {
		attemptCtx, cancel := context.WithTimeout(ctx, effectiveTimeout)
		req, err := c.newRequest(attemptCtx, method, path, bodyBytes, opts.headers)
		if err != nil {
			cancel()
			return err
		}

		resp, err := c.config.httpClient.Do(req)
		if err != nil {
			cancel()
			if isTimeoutError(err) {
				return &TimeoutError{Op: method, URL: req.URL.String(), Timeout: effectiveTimeout, Err: err}
			}
			lastErr = &ConnectionError{Op: method, URL: req.URL.String(), Err: err}
			if attempt < effectiveRetries {
				if waitErr := sleepWithContext(ctx, retryDelay(attempt, nil)); waitErr != nil {
					return waitErr
				}
				continue
			}
			return lastErr
		}

		respBody, readErr := io.ReadAll(resp.Body)
		closeErr := resp.Body.Close()
		cancel()
		if readErr != nil {
			return &ConnectionError{Op: method, URL: req.URL.String(), Err: readErr}
		}
		if closeErr != nil {
			return &ConnectionError{Op: method, URL: req.URL.String(), Err: closeErr}
		}
		if isRetryableStatus(resp.StatusCode) && attempt < effectiveRetries {
			delay := retryDelay(attempt, parseRetryAfter(resp.Header, time.Now()))
			if waitErr := sleepWithContext(ctx, delay); waitErr != nil {
				return waitErr
			}
			continue
		}
		return parseEnvelope(resp, respBody, out)
	}

	if lastErr != nil {
		return lastErr
	}
	return &ConnectionError{Op: method, URL: c.url(path), Err: fmt.Errorf("request failed after retries")}
}

func (c *Client) newRequest(ctx context.Context, method, path string, body []byte, requestHeaders http.Header) (*http.Request, error) {
	var reader io.Reader
	if body != nil {
		reader = bytes.NewReader(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.url(path), reader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", c.config.userAgent)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if c.config.apiToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.config.apiToken)
	}
	for key, values := range c.config.headers {
		for _, value := range values {
			req.Header.Add(key, value)
		}
	}
	for key, values := range requestHeaders {
		for _, value := range values {
			req.Header.Add(key, value)
		}
	}
	return req, nil
}

func (c *Client) url(path string) string {
	normalized := path
	if !strings.HasPrefix(normalized, "/") {
		normalized = "/" + normalized
	}
	return c.config.baseURL + "/open/v1" + normalized
}

func parseEnvelope(resp *http.Response, body []byte, out any) error {
	requestID := resp.Header.Get("X-Request-Id")
	contentType := resp.Header.Get("Content-Type")
	if len(body) > 0 && !strings.Contains(strings.ToLower(contentType), "application/json") {
		return &APIError{StatusCode: resp.StatusCode, ErrorCode: "parse_error", Message: "non-JSON response", RequestID: requestID, Body: body}
	}
	var envelope rawEnvelope
	if err := json.Unmarshal(body, &envelope); err != nil {
		return &APIError{StatusCode: resp.StatusCode, ErrorCode: "parse_error", Message: "failed to parse response JSON", RequestID: requestID, Body: body}
	}
	if envelope.Error != nil {
		apiErr := &APIError{
			StatusCode: resp.StatusCode,
			ErrorCode:  envelope.Error.Code,
			Message:    envelope.Error.Message,
			RequestID:  requestID,
			Details:    envelope.Error.Details,
			Body:       body,
			RetryAfter: parseRetryAfter(resp.Header, time.Now()),
		}
		if resp.StatusCode == http.StatusUnauthorized {
			return &AuthenticationError{APIError: apiErr}
		}
		if resp.StatusCode == http.StatusTooManyRequests {
			return &RateLimitError{APIError: apiErr}
		}
		return apiErr
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return &APIError{StatusCode: resp.StatusCode, ErrorCode: "http_error", Message: resp.Status, RequestID: requestID, Body: body}
	}
	if envelope.Data == nil {
		return &APIError{StatusCode: resp.StatusCode, ErrorCode: "parse_error", Message: "missing data envelope", RequestID: requestID, Body: body}
	}
	if out == nil {
		return nil
	}
	if err := rejectNullRequiredArrays(*envelope.Data, out); err != nil {
		return &APIError{StatusCode: resp.StatusCode, ErrorCode: "parse_error", Message: err.Error(), RequestID: requestID, Body: body}
	}
	if err := json.Unmarshal(*envelope.Data, out); err != nil {
		return &APIError{StatusCode: resp.StatusCode, ErrorCode: "parse_error", Message: "failed to decode data envelope", RequestID: requestID, Body: body}
	}
	normalizeResponse(out)
	return nil
}

func normalizeResponse(out any) {
	switch response := out.(type) {
	case *ExtractMemoryResponse:
		if response.RawDataIDs == nil {
			response.RawDataIDs = []string{}
		}
		if response.ItemIDs == nil {
			response.ItemIDs = []int64{}
		}
		if response.InsightIDs == nil {
			response.InsightIDs = []int64{}
		}
	case *RetrieveMemoryResponse:
		if response.Items == nil {
			response.Items = []RetrievedItem{}
		}
		if response.Insights == nil {
			response.Insights = []RetrievedInsight{}
		}
		if response.RawData == nil {
			response.RawData = []RetrievedRawData{}
		}
		if response.Evidences == nil {
			response.Evidences = []string{}
		}
		if response.Trace != nil && response.Trace.Stages == nil {
			response.Trace.Stages = []StageView{}
		}
	}
}

func rejectNullRequiredArrays(data json.RawMessage, out any) error {
	var obj map[string]json.RawMessage
	if err := json.Unmarshal(data, &obj); err != nil || obj == nil {
		return nil
	}
	required := requiredArrayFields(out)
	for _, field := range required {
		raw, ok := obj[field]
		if !ok {
			continue
		}
		if bytes.Equal(bytes.TrimSpace(raw), []byte("null")) {
			return fmt.Errorf("required array field %q must not be null", field)
		}
	}
	if _, ok := out.(*RetrieveMemoryResponse); ok {
		if traceRaw, ok := obj["trace"]; ok && !bytes.Equal(bytes.TrimSpace(traceRaw), []byte("null")) {
			var trace map[string]json.RawMessage
			if err := json.Unmarshal(traceRaw, &trace); err == nil && trace != nil {
				if stages, ok := trace["stages"]; ok && bytes.Equal(bytes.TrimSpace(stages), []byte("null")) {
					return fmt.Errorf(`required array field "trace.stages" must not be null`)
				}
			}
		}
	}
	return nil
}

func requiredArrayFields(out any) []string {
	switch out.(type) {
	case *ExtractMemoryResponse:
		return []string{"rawDataIds", "itemIds", "insightIds"}
	case *RetrieveMemoryResponse:
		return []string{"items", "insights", "rawData", "evidences"}
	default:
		return nil
	}
}

func isTimeoutError(err error) bool {
	if err == nil {
		return false
	}
	if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
		return true
	}
	if urlErr, ok := err.(*url.Error); ok {
		return isTimeoutError(urlErr.Err)
	}
	return false
}

func sleepWithContext(ctx context.Context, delay time.Duration) error {
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
