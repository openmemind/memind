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
	"encoding/json"
	"fmt"
	"io"
	"strings"
)

func validateExtractRequest(req ExtractMemoryRequest) error {
	var issues []ValidationIssue
	requireNonBlank(&issues, "userId", req.UserID)
	requireNonBlank(&issues, "agentId", req.AgentID)
	if req.RawContent == nil {
		issues = append(issues, ValidationIssue{Field: "rawContent", Message: "rawContent is required"})
	} else if err := validateRawContent(req.RawContent); err != nil {
		issues = append(issues, ValidationIssue{Field: "rawContent", Message: err.Error()})
	}
	return validationErrorOrNil(issues)
}

func validateAddMessageRequest(req AddMessageRequest) error {
	var issues []ValidationIssue
	requireNonBlank(&issues, "userId", req.UserID)
	requireNonBlank(&issues, "agentId", req.AgentID)
	if err := validateMessage(req.Message, "message"); err != nil {
		issues = append(issues, ValidationIssue{Field: "message", Message: err.Error()})
	}
	return validationErrorOrNil(issues)
}

func validateCommitRequest(req CommitMemoryRequest) error {
	var issues []ValidationIssue
	requireNonBlank(&issues, "userId", req.UserID)
	requireNonBlank(&issues, "agentId", req.AgentID)
	return validationErrorOrNil(issues)
}

func validateRetrieveRequest(req RetrieveMemoryRequest) error {
	var issues []ValidationIssue
	requireNonBlank(&issues, "userId", req.UserID)
	requireNonBlank(&issues, "agentId", req.AgentID)
	requireNonBlank(&issues, "query", req.Query)
	if strings.TrimSpace(string(req.Strategy)) == "" {
		issues = append(issues, ValidationIssue{Field: "strategy", Message: "strategy is required"})
	}
	return validationErrorOrNil(issues)
}

func validateRawContent(raw RawContent) error {
	payload, err := raw.MarshalJSON()
	if err != nil {
		return fmt.Errorf("marshal raw content: %w", err)
	}
	var obj map[string]json.RawMessage
	decoder := json.NewDecoder(bytes.NewReader(payload))
	if err := decoder.Decode(&obj); err != nil {
		return fmt.Errorf("raw content must be a JSON object: %w", err)
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return fmt.Errorf("raw content must contain exactly one JSON value")
	}
	if obj == nil {
		return fmt.Errorf("raw content must be a JSON object")
	}
	var contentType string
	if err := json.Unmarshal(obj["type"], &contentType); err != nil || strings.TrimSpace(contentType) == "" {
		return fmt.Errorf("raw content type must be a non-blank string")
	}
	if contentType == "conversation" {
		if conv, ok := raw.(ConversationContent); ok {
			if len(conv.Messages) == 0 {
				return fmt.Errorf("conversation raw content requires non-empty messages")
			}
			for i, message := range conv.Messages {
				if err := validateMessage(message, fmt.Sprintf("messages[%d]", i)); err != nil {
					return err
				}
			}
			return nil
		}
		messagesRaw, ok := obj["messages"]
		if !ok {
			return fmt.Errorf("conversation raw content requires non-empty messages")
		}
		var messages []json.RawMessage
		if err := json.Unmarshal(messagesRaw, &messages); err != nil || len(messages) == 0 {
			return fmt.Errorf("conversation raw content requires non-empty messages")
		}
		for i, messageRaw := range messages {
			if err := validateMarshaledConversationMessage(messageRaw, fmt.Sprintf("messages[%d]", i)); err != nil {
				return err
			}
		}
	}
	return nil
}

func validateMarshaledConversationMessage(raw json.RawMessage, field string) error {
	var message struct {
		Role    Role              `json:"role"`
		Content []json.RawMessage `json:"content"`
	}
	if err := json.Unmarshal(raw, &message); err != nil {
		return fmt.Errorf("%s must be a valid message object: %w", field, err)
	}
	if message.Role != RoleUser && message.Role != RoleAssistant {
		return fmt.Errorf("%s.role must be USER or ASSISTANT", field)
	}
	if len(message.Content) == 0 {
		return fmt.Errorf("%s.content must be non-empty", field)
	}
	for i, blockRaw := range message.Content {
		if err := validateMarshaledContentBlock(blockRaw); err != nil {
			return fmt.Errorf("%s.content[%d]: %w", field, i, err)
		}
	}
	return nil
}

func validateMarshaledContentBlock(raw json.RawMessage) error {
	var block map[string]json.RawMessage
	if err := json.Unmarshal(raw, &block); err != nil || block == nil {
		return fmt.Errorf("content block must be an object")
	}
	var typ string
	if err := json.Unmarshal(block["type"], &typ); err != nil || strings.TrimSpace(typ) == "" {
		return fmt.Errorf("content block type must be valid")
	}
	switch ContentBlockType(typ) {
	case "text":
		textRaw, ok := block["text"]
		if !ok || bytes.Equal(bytes.TrimSpace(textRaw), []byte("null")) {
			return fmt.Errorf("text content block requires text")
		}
		var text string
		if err := json.Unmarshal(textRaw, &text); err != nil {
			return fmt.Errorf("text content block requires text")
		}
	case ContentBlockImage, ContentBlockAudio, ContentBlockVideo:
		var media MediaContentBlock
		if err := json.Unmarshal(raw, &media); err != nil {
			return fmt.Errorf("media content block is invalid: %w", err)
		}
		return validateSource(media.Source)
	default:
		return fmt.Errorf("unsupported content block type %q", typ)
	}
	return nil
}

func validateMessage(message Message, field string) error {
	if message.Role != RoleUser && message.Role != RoleAssistant {
		return fmt.Errorf("%s.role must be USER or ASSISTANT", field)
	}
	if len(message.Content) == 0 {
		return fmt.Errorf("%s.content must be non-empty", field)
	}
	for i, block := range message.Content {
		if err := validateContentBlock(block); err != nil {
			return fmt.Errorf("%s.content[%d]: %w", field, i, err)
		}
	}
	return nil
}

func validateContentBlock(block ContentBlock) error {
	if block == nil {
		return fmt.Errorf("content block is required")
	}
	switch b := block.(type) {
	case TextContentBlock:
		return nil
	case MediaContentBlock:
		if b.Type != ContentBlockImage && b.Type != ContentBlockAudio && b.Type != ContentBlockVideo {
			return fmt.Errorf("unsupported media content block type %q", b.Type)
		}
		return validateSource(b.Source)
	default:
		payload, err := block.MarshalJSON()
		if err != nil {
			return fmt.Errorf("marshal content block: %w", err)
		}
		var obj map[string]json.RawMessage
		if err := json.Unmarshal(payload, &obj); err != nil || obj == nil {
			return fmt.Errorf("content block must marshal to an object")
		}
		var typ string
		if err := json.Unmarshal(obj["type"], &typ); err != nil || strings.TrimSpace(typ) == "" {
			return fmt.Errorf("content block type must be valid")
		}
		return nil
	}
}

func validateSource(source Source) error {
	switch source.Type {
	case SourceURL:
		if strings.TrimSpace(source.URL) == "" {
			return fmt.Errorf("url source requires a non-blank url")
		}
	case SourceBase64:
		if strings.TrimSpace(source.MediaType) == "" {
			return fmt.Errorf("base64 source requires a non-blank media_type")
		}
		if source.Data == "" {
			return fmt.Errorf("base64 source requires data")
		}
	default:
		return fmt.Errorf("unsupported source type %q", source.Type)
	}
	return nil
}

func requireNonBlank(issues *[]ValidationIssue, field, value string) {
	if strings.TrimSpace(value) == "" {
		*issues = append(*issues, ValidationIssue{Field: field, Message: field + " is required"})
	}
}

func validationErrorOrNil(issues []ValidationIssue) error {
	if len(issues) == 0 {
		return nil
	}
	return &ValidationError{Issues: issues}
}
