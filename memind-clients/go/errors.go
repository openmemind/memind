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
	"encoding/json"
	"fmt"
	"strings"
	"time"
)

type Error struct {
	Message string
}

func (e *Error) Error() string {
	if e == nil {
		return ""
	}
	return e.Message
}

type APIError struct {
	StatusCode int
	ErrorCode  string
	Message    string
	RequestID  string
	Details    json.RawMessage
	Body       []byte
	RetryAfter *time.Duration
}

func (e *APIError) Error() string {
	if e == nil {
		return ""
	}
	if e.ErrorCode != "" {
		return fmt.Sprintf("memind API error: status=%d code=%s message=%s", e.StatusCode, e.ErrorCode, e.Message)
	}
	return fmt.Sprintf("memind API error: status=%d message=%s", e.StatusCode, e.Message)
}

type AuthenticationError struct {
	*APIError
}

func (e *AuthenticationError) Unwrap() error {
	if e == nil {
		return nil
	}
	return e.APIError
}

type RateLimitError struct {
	*APIError
}

func (e *RateLimitError) Unwrap() error {
	if e == nil {
		return nil
	}
	return e.APIError
}

type ConnectionError struct {
	Op  string
	URL string
	Err error
}

func (e *ConnectionError) Error() string {
	return fmt.Sprintf("memind connection error: %s %s: %v", e.Op, e.URL, e.Err)
}

func (e *ConnectionError) Unwrap() error {
	return e.Err
}

type TimeoutError struct {
	Op      string
	URL     string
	Timeout time.Duration
	Err     error
}

func (e *TimeoutError) Error() string {
	return fmt.Sprintf("memind timeout: %s %s after %s", e.Op, e.URL, e.Timeout)
}

func (e *TimeoutError) Unwrap() error {
	return e.Err
}

type ValidationIssue struct {
	Field   string
	Message string
}

type ValidationError struct {
	Issues []ValidationIssue
}

func (e *ValidationError) Error() string {
	if e == nil || len(e.Issues) == 0 {
		return "memind validation error"
	}
	parts := make([]string, 0, len(e.Issues))
	for _, issue := range e.Issues {
		parts = append(parts, issue.Field+": "+issue.Message)
	}
	return "memind validation error: " + strings.Join(parts, "; ")
}
