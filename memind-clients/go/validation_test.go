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

import "testing"

func TestValidateMessageRejectsInvalidBlocks(t *testing.T) {
	cases := []Message{
		{},
		{Role: Role("SYSTEM"), Content: []ContentBlock{TextBlock("x")}},
		{Role: RoleUser},
		{Role: RoleUser, Content: []ContentBlock{MediaContentBlock{Type: "pdf"}}},
		{Role: RoleUser, Content: []ContentBlock{ImageURL(" ")}},
		{Role: RoleUser, Content: []ContentBlock{ImageBase64("", "abc")}},
		{Role: RoleUser, Content: []ContentBlock{ImageBase64("image/png", "")}},
	}
	for _, msg := range cases {
		if err := validateMessage(msg, "message"); err == nil {
			t.Fatalf("validateMessage(%#v) error = nil, want error", msg)
		}
	}
}

func TestValidateRequestAggregatesIssues(t *testing.T) {
	err := validateExtractRequest(ExtractMemoryRequest{})
	if err == nil {
		t.Fatal("validateExtractRequest error = nil, want error")
	}
	validationErr, ok := err.(*ValidationError)
	if !ok {
		t.Fatalf("error type = %T, want *ValidationError", err)
	}
	if len(validationErr.Issues) < 3 {
		t.Fatalf("issues = %#v, want multiple issues", validationErr.Issues)
	}
}

func TestValidateExtractAcceptsConversation(t *testing.T) {
	err := validateExtractRequest(ExtractMemoryRequest{
		UserID:     "u",
		AgentID:    "a",
		RawContent: Conversation(UserMessage("remember this")),
	})
	if err != nil {
		t.Fatalf("validateExtractRequest conversation error = %v", err)
	}
}
