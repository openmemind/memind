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
	"testing"
)

type customRawContent struct{}

func (customRawContent) MarshalJSON() ([]byte, error) {
	return []byte(`{"type":"plugin","value":1}`), nil
}

func TestTextBlockSerializesTextEvenWhenEmpty(t *testing.T) {
	got, err := json.Marshal(TextBlock(""))
	if err != nil {
		t.Fatalf("Marshal TextBlock error = %v", err)
	}
	want := `{"text":"","type":"text"}`
	if string(got) != want {
		t.Fatalf("TextBlock JSON = %s, want %s", got, want)
	}
}

func TestConversationRawContentSerializes(t *testing.T) {
	got, err := json.Marshal(Conversation(UserMessage("remember this")))
	if err != nil {
		t.Fatalf("Marshal Conversation error = %v", err)
	}
	want := `{"messages":[{"role":"USER","content":[{"text":"remember this","type":"text"}]}],"type":"conversation"}`
	if string(got) != want {
		t.Fatalf("Conversation JSON = %s, want %s", got, want)
	}
}

func TestRawMapFlattensFields(t *testing.T) {
	raw, err := RawMap("document", map[string]any{"title": "T", "body": "B"})
	if err != nil {
		t.Fatalf("RawMap error = %v", err)
	}
	got, err := json.Marshal(raw)
	if err != nil {
		t.Fatalf("Marshal RawMap error = %v", err)
	}
	want := `{"body":"B","title":"T","type":"document"}`
	if string(got) != want {
		t.Fatalf("RawMap JSON = %s, want %s", got, want)
	}
}

func TestRawMapRejectsInvalidInputs(t *testing.T) {
	cases := []struct {
		name string
		typ  string
		body map[string]any
	}{
		{name: "blank type", typ: " ", body: nil},
		{name: "conversation type", typ: "conversation", body: nil},
		{name: "type field", typ: "document", body: map[string]any{"type": "bad"}},
		{name: "unencodable", typ: "document", body: map[string]any{"bad": func() {}}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := RawMap(tc.typ, tc.body); err == nil {
				t.Fatal("RawMap error = nil, want error")
			}
		})
	}
}

func TestValidateRawContentAcceptsCustomMarshaler(t *testing.T) {
	if err := validateRawContent(customRawContent{}); err != nil {
		t.Fatalf("validateRawContent(custom) error = %v", err)
	}
}

func TestValidateRawContentRejectsBadRawJSON(t *testing.T) {
	for _, raw := range []RawJSON{
		{Value: json.RawMessage(`[]`)},
		{Value: json.RawMessage(`{"type":" "}`)},
		{Value: json.RawMessage(`{"type":"document"} {}`)},
		{Value: json.RawMessage(`{"type":"conversation","messages":[]}`)},
		{Value: json.RawMessage(`{"type":"conversation","messages":[{"role":"USER","content":[{"type":"text","text":null}]}]}`)},
		{Value: json.RawMessage(`{"type":"conversation","messages":[{"role":"USER","content":[{"type":"image","source":{"type":"url","url":" "}}]}]}`)},
	} {
		if err := validateRawContent(raw); err == nil {
			t.Fatalf("validateRawContent(%s) error = nil, want error", raw.Value)
		}
	}
}
