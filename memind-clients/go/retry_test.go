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
	"errors"
	"net/http"
	"testing"
	"time"
)

func TestIsRetryableStatus(t *testing.T) {
	for _, status := range []int{408, 429, 500, 502, 503, 504} {
		if !isRetryableStatus(status) {
			t.Fatalf("isRetryableStatus(%d) = false", status)
		}
	}
	for _, status := range []int{400, 401, 403, 404, 409, 422, 501} {
		if isRetryableStatus(status) {
			t.Fatalf("isRetryableStatus(%d) = true", status)
		}
	}
}

func TestParseRetryAfter(t *testing.T) {
	headers := http.Header{}
	headers.Set("Retry-After", "3")
	got := parseRetryAfter(headers, time.Now())
	if got == nil || *got != 3*time.Second {
		t.Fatalf("Retry-After seconds = %v, want 3s", got)
	}

	when := time.Now().Add(5 * time.Second).UTC().Format(http.TimeFormat)
	headers.Set("Retry-After", when)
	got = parseRetryAfter(headers, time.Now())
	if got == nil || *got <= 0 {
		t.Fatalf("Retry-After date = %v, want positive duration", got)
	}
}

func TestAPIErrorSupportsErrorsAs(t *testing.T) {
	base := &APIError{StatusCode: 401, ErrorCode: "unauthorized", Message: "bad token"}
	err := &AuthenticationError{APIError: base}

	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatal("errors.As(AuthenticationError, *APIError) = false")
	}
	if apiErr.StatusCode != 401 {
		t.Fatalf("StatusCode = %d", apiErr.StatusCode)
	}
}
