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

type ContentBlock interface {
	contentBlock()
	json.Marshaler
}

type TextContentBlock struct {
	Text string
}

func (TextContentBlock) contentBlock() {}

func (b TextContentBlock) MarshalJSON() ([]byte, error) {
	return json.Marshal(struct {
		Text string `json:"text"`
		Type string `json:"type"`
	}{Text: b.Text, Type: "text"})
}

type ContentBlockType string

const (
	ContentBlockImage ContentBlockType = "image"
	ContentBlockAudio ContentBlockType = "audio"
	ContentBlockVideo ContentBlockType = "video"
)

type MediaContentBlock struct {
	Type   ContentBlockType
	Source Source
}

func (MediaContentBlock) contentBlock() {}

func (b MediaContentBlock) MarshalJSON() ([]byte, error) {
	return json.Marshal(struct {
		Source Source           `json:"source"`
		Type   ContentBlockType `json:"type"`
	}{Source: b.Source, Type: b.Type})
}

type SourceType string

const (
	SourceURL    SourceType = "url"
	SourceBase64 SourceType = "base64"
)

type Source struct {
	Type      SourceType `json:"type"`
	URL       string     `json:"url,omitempty"`
	MediaType string     `json:"media_type,omitempty"`
	Data      string     `json:"data,omitempty"`
}

type MessageOption func(*Message)

func WithMessageTimestamp(timestamp time.Time) MessageOption {
	return func(msg *Message) {
		msg.Timestamp = &timestamp
	}
}

func WithMessageUserName(userName string) MessageOption {
	return func(msg *Message) {
		msg.UserName = userName
	}
}

func WithMessageSourceClient(sourceClient string) MessageOption {
	return func(msg *Message) {
		msg.SourceClient = sourceClient
	}
}

func UserMessage(text string, opts ...MessageOption) Message {
	return messageWithText(RoleUser, text, opts...)
}

func AssistantMessage(text string, opts ...MessageOption) Message {
	return messageWithText(RoleAssistant, text, opts...)
}

func messageWithText(role Role, text string, opts ...MessageOption) Message {
	msg := Message{Role: role, Content: []ContentBlock{TextBlock(text)}}
	for _, opt := range opts {
		if opt != nil {
			opt(&msg)
		}
	}
	return msg
}

func TextBlock(text string) ContentBlock {
	return TextContentBlock{Text: text}
}

func ImageURL(url string) ContentBlock {
	return MediaContentBlock{Type: ContentBlockImage, Source: Source{Type: SourceURL, URL: url}}
}

func ImageBase64(mediaType, data string) ContentBlock {
	return MediaContentBlock{Type: ContentBlockImage, Source: Source{Type: SourceBase64, MediaType: mediaType, Data: data}}
}

func AudioURL(url string) ContentBlock {
	return MediaContentBlock{Type: ContentBlockAudio, Source: Source{Type: SourceURL, URL: url}}
}

func AudioBase64(mediaType, data string) ContentBlock {
	return MediaContentBlock{Type: ContentBlockAudio, Source: Source{Type: SourceBase64, MediaType: mediaType, Data: data}}
}

func VideoURL(url string) ContentBlock {
	return MediaContentBlock{Type: ContentBlockVideo, Source: Source{Type: SourceURL, URL: url}}
}

func VideoBase64(mediaType, data string) ContentBlock {
	return MediaContentBlock{Type: ContentBlockVideo, Source: Source{Type: SourceBase64, MediaType: mediaType, Data: data}}
}

type RawContent interface {
	json.Marshaler
}

type ConversationContent struct {
	Messages []Message `json:"messages"`
}

func Conversation(messages ...Message) ConversationContent {
	return ConversationContent{Messages: messages}
}

func (c ConversationContent) MarshalJSON() ([]byte, error) {
	return json.Marshal(struct {
		Messages []Message `json:"messages"`
		Type     string    `json:"type"`
	}{Messages: c.Messages, Type: "conversation"})
}

type MapRawContent struct {
	Type   string
	Fields map[string]any
}

func RawMap(contentType string, fields map[string]any) (MapRawContent, error) {
	contentType = strings.TrimSpace(contentType)
	if contentType == "" {
		return MapRawContent{}, fmt.Errorf("raw content type must not be blank")
	}
	if contentType == "conversation" {
		return MapRawContent{}, fmt.Errorf(`raw content type "conversation" must use Conversation`)
	}
	if _, exists := fields["type"]; exists {
		return MapRawContent{}, fmt.Errorf(`raw content fields must not contain "type"`)
	}
	if fields != nil {
		if _, err := json.Marshal(fields); err != nil {
			return MapRawContent{}, fmt.Errorf("raw content fields must be JSON-serializable: %w", err)
		}
	}
	return MapRawContent{Type: contentType, Fields: fields}, nil
}

func MustRawMap(contentType string, fields map[string]any) MapRawContent {
	raw, err := RawMap(contentType, fields)
	if err != nil {
		panic(err)
	}
	return raw
}

func (m MapRawContent) MarshalJSON() ([]byte, error) {
	obj := make(map[string]any, len(m.Fields)+1)
	for key, value := range m.Fields {
		obj[key] = value
	}
	obj["type"] = m.Type
	return json.Marshal(obj)
}

type RawJSON struct {
	Value json.RawMessage
}

func (r RawJSON) MarshalJSON() ([]byte, error) {
	return r.Value.MarshalJSON()
}
