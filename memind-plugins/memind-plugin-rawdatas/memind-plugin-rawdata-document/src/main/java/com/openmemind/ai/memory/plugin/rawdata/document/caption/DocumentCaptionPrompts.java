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
package com.openmemind.ai.memory.plugin.rawdata.document.caption;

import com.openmemind.ai.memory.core.prompt.PromptResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plugin-owned prompts for summarizing document chunks.
 */
public final class DocumentCaptionPrompts {

    private static final String CHUNK_OBJECTIVE =
            """
            You are an expert in document analysis and knowledge distillation. Your task is to \
            analyze a single document chunk and produce a structured retrieval summary.

            This summary is the contextual anchor for memory items extracted from this chunk. \
            When a retrieval system finds a related item, it returns this summary so the reader \
            can understand the relevant context without reopening the original text or the full \
            document.

            Summarize only the provided chunk. Do NOT reconstruct unseen sections, infer broader \
            document intent, or pretend you have read the whole document.
            """;

    private static final String WHOLE_DOCUMENT_OBJECTIVE =
            """
            You are an expert in document analysis and knowledge distillation. Your task is to \
            analyze a whole document and produce a structured retrieval summary.

            This summary is the contextual anchor for memory items extracted from this document. \
            When a retrieval system finds a related item, it returns this summary so the reader \
            can understand the relevant context without reopening the original text.

            Summarize the provided document faithfully. Do NOT invent facts, hidden sections, or \
            unsupported motivations.
            """;

    private static final String CHUNK_GUIDELINES =
            """
            # Guidelines

            ## 1. Chunk Scope
            - Summarize only the provided chunk, not the whole document.
            - If structure hints such as heading, section, page range, or row range are \
            provided, use them to situate the chunk, but only when they are consistent with \
            the chunk text itself.
            - If the chunk is clearly partial, describe what this chunk covers instead of \
            guessing what comes before or after it.

            ## 2. Distill, Don't Transcribe
            - Distill the central topic, concrete facts, procedures, arguments, constraints, \
            and conclusions contained in the chunk.
            - Remove boilerplate, repetition, formatting noise, and low-value connective text.
            - Do NOT rewrite the chunk line by line, sentence by sentence, or row by row.
            - For config, code, SQL, tables, or OCR-heavy content, summarize what the material \
            says or configures instead of echoing every token.

            ## 3. Retrieval Optimization
            Preserve names, paths, URLs, versions, dates, numbers, and config keys exactly.
            These are high-value retrieval anchors. Do NOT paraphrase:
            - API names, class names, method names, enum names, SQL table or column names
            - File paths, URLs, environment variables, CLI flags, configuration keys
            - Product names, project names, version numbers, dates, quantities, thresholds

            ## 4. Faithfulness
            - Stay 100% faithful to the chunk.
            - Do not invent conclusions or commentary.
            - Do NOT infer broader document intent, author motivation, missing sections, or \
            unstated implications.
            - Do NOT use phrases like "this document shows", "the author likely means", or \
            "this implies that" unless the chunk explicitly says so.

            ## 5. Writing Style
            - Write a concise, retrieval-oriented summary, not a transcript or long paraphrase.
            - The title should be specific enough to disambiguate this chunk from neighboring \
            chunks.
            - The content must be a single coherent paragraph, not bullets or section headers.
            """;

    private static final String WHOLE_DOCUMENT_GUIDELINES =
            """
            # Guidelines

            ## 1. Document Scope
            - Summarize the provided whole document, not a hypothetical broader corpus.
            - Use structure hints such as title, section names, headings, page ranges, and row \
            ranges only when they are grounded in the provided content.
            - Describe the document's main subject, important sections, concrete facts, \
            procedures, arguments, constraints, and conclusions.

            ## 2. Distill, Don't Transcribe
            - Distill the document into a concise retrieval-oriented summary rather than \
            rewriting it section by section.
            - Remove boilerplate, repetition, formatting noise, and low-value connective text.
            - For config, code, SQL, tables, or OCR-heavy material, summarize what the document \
            configures or states instead of echoing raw text line by line.

            ## 3. Retrieval Optimization
            Preserve names, paths, URLs, versions, dates, numbers, and config keys exactly.
            These are high-value retrieval anchors. Do NOT paraphrase:
            - API names, class names, method names, enum names, SQL table or column names
            - File paths, URLs, environment variables, CLI flags, configuration keys
            - Product names, project names, version numbers, dates, quantities, thresholds

            ## 4. Faithfulness
            - Stay 100% faithful to the provided document.
            - Do not invent conclusions or commentary that are not supported by the text.
            - Do not speculate about the author's intent beyond what the document states.

            ## 5. Writing Style
            - Write a concise, retrieval-oriented summary, not a transcript or outline.
            - The title should identify the document's main subject clearly.
            - The content must be a single coherent paragraph, not bullets or section headers.
            """;

    private static final String OUTPUT =
            """
            # Output Format

            Return ONLY a JSON object with "title" and "content".
            Return valid JSON ONLY. No markdown fences, no surrounding text.
            {
              "title": "Specific chunk topic or document subsection",
              "content": "One coherent paragraph under 180 words"
            }
            """;

    private static final String CHUNK_EXAMPLES =
            """
            # Examples

            ## Good Example

            Input chunk: A section titled "Retry policy" that defines \
            `spring.ai.retry.max-attempts=5`, uses exponential backoff for HTTP 429 and 503 \
            responses, and states that `Retry-After` should be honored when present.

            Output:
            {
              "title": "Retry policy for HTTP 429/503 handling",
              "content": "This chunk defines the retry behavior for transient failures in the \
            Spring AI integration. It sets `spring.ai.retry.max-attempts=5`, uses exponential \
            backoff for HTTP 429 and 503 responses, and specifies that the `Retry-After` header \
            should be honored when present. The section frames these settings as the default \
            policy for handling rate limits and temporary service unavailability."
            }

            Why this is good:
            - Focuses on the current chunk instead of inventing the rest of the document
            - Preserves exact retrieval anchors such as `spring.ai.retry.max-attempts=5` and \
            `Retry-After`
            - Distills the policy instead of copying the chunk line by line

            ## Bad Example 1: Too close to transcription

            {
              "title": "Retry policy",
              "content": "The chunk says spring.ai.retry.max-attempts=5. Then it says to use \
            exponential backoff. Then it says to retry 429 and 503. Then it says to honor \
            Retry-After."
            }

            -> Wrong: This is a line-by-line restatement, not a distilled retrieval summary.

            ## Bad Example 2: Overreaches beyond the chunk

            {
              "title": "Global resilience architecture",
              "content": "This document explains the system's overall resilience strategy and \
            shows that the team prioritizes reliability across every external dependency."
            }

            -> Wrong: The chunk may only cover one retry subsection. "overall resilience \
            strategy" and team priorities are unsupported extrapolations.

            ## Bad Example 3: Loses technical anchors

            {
              "title": "Error handling settings",
              "content": "This chunk describes how the application retries temporary request \
            failures with delay settings."
            }

            -> Wrong: It removed the exact identifiers and values that future retrieval depends on.
            """;

    private static final String WHOLE_DOCUMENT_EXAMPLES =
            """
            # Examples

            ## Good Example

            Input document: A deployment guide that covers environment setup, required secrets, \
            `docker compose up -d`, health checks, rollback steps, and troubleshooting notes for \
            `OPENAI_API_KEY` and `SPRING_PROFILES_ACTIVE`.

            Output:
            {
              "title": "Deployment guide for local startup and rollback",
              "content": "This document is a deployment guide covering environment preparation, \
            required secrets, startup with `docker compose up -d`, health-check steps, rollback \
            instructions, and troubleshooting for configuration such as `OPENAI_API_KEY` and \
            `SPRING_PROFILES_ACTIVE`. It frames the expected bring-up flow, verification steps, \
            and recovery actions needed to operate the service safely in a local environment."
            }

            Why this is good:
            - Summarizes the whole document rather than a single subsection
            - Preserves exact retrieval anchors such as `docker compose up -d`, \
            `OPENAI_API_KEY`, and `SPRING_PROFILES_ACTIVE`
            - Distills the document into one retrieval-oriented paragraph
            """;

    private static final String CHUNK_SELF_CHECK =
            """
            # Self-Check

            Before outputting, verify:
            - Is the output strictly valid JSON with "title" and "content" fields only?
            - Does the summary stay within the provided chunk instead of the whole document?
            - Is the content a distilled paragraph rather than a transcription or bullet list?
            - Are exact anchors such as names, URLs, paths, config keys, identifiers, dates, \
            and numbers preserved?
            - Is the text free of unsupported commentary, speculation, and broader-document \
            inference?
            """;

    private static final String WHOLE_DOCUMENT_SELF_CHECK =
            """
            # Self-Check

            Before outputting, verify:
            - Is the output strictly valid JSON with "title" and "content" fields only?
            - Does the summary stay faithful to the provided whole document?
            - Is the content a distilled paragraph rather than a transcription or outline?
            - Are exact anchors such as names, URLs, paths, config keys, identifiers, dates, \
            and numbers preserved?
            - Is the text free of unsupported commentary and speculation?
            """;

    private DocumentCaptionPrompts() {}

    public static PromptResult build(
            String content, Map<String, Object> metadata, String language) {
        boolean wholeDocument = isWholeDocument(metadata);
        String userPrompt =
                """
                Summarize the following %s.

                %s<CONTENT>
                %s
                </CONTENT>
                """
                        .formatted(
                                wholeDocument ? "document" : "document chunk",
                                structureHints(metadata),
                                content == null ? "" : content);
        return PromptResult.of(systemPrompt(wholeDocument), userPrompt, language);
    }

    private static String systemPrompt(boolean wholeDocument) {
        return String.join(
                "\n\n",
                wholeDocument ? WHOLE_DOCUMENT_OBJECTIVE : CHUNK_OBJECTIVE,
                wholeDocument ? WHOLE_DOCUMENT_GUIDELINES : CHUNK_GUIDELINES,
                OUTPUT,
                wholeDocument ? WHOLE_DOCUMENT_EXAMPLES : CHUNK_EXAMPLES,
                wholeDocument ? WHOLE_DOCUMENT_SELF_CHECK : CHUNK_SELF_CHECK);
    }

    private static boolean isWholeDocument(Map<String, Object> metadata) {
        return "document-whole".equals(metadataText(metadata, "chunkStrategy"));
    }

    private static String structureHints(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        List<String> hints = new ArrayList<>();
        addHint(hints, "Heading", metadataText(metadata, "headingTitle"));
        addHint(hints, "Section", metadataText(metadata, "sectionTitle"));
        addHint(
                hints,
                null,
                rangeLabel(
                        "Page",
                        metadata.get("pageNumber"),
                        metadata.get("pageStart"),
                        metadata.get("pageEnd")));
        addHint(
                hints,
                null,
                rangeLabel("Row", null, metadata.get("rowStart"), metadata.get("rowEnd")));
        if (hints.isEmpty()) {
            return "";
        }
        return "Known structure hints:\n- " + String.join("\n- ", hints) + "\n\n";
    }

    private static void addHint(List<String> hints, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        hints.add(label == null || label.isBlank() ? value : label + ": " + value);
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String rangeLabel(
            String singular, Object singleValue, Object startValue, Object endValue) {
        Integer single = positiveInt(singleValue);
        if (single != null) {
            return singular + " " + single;
        }
        Integer start = positiveInt(startValue);
        Integer end = positiveInt(endValue);
        if (start == null && end == null) {
            return null;
        }
        if (start != null && end != null) {
            return start.equals(end) ? singular + " " + start : singular + "s " + start + "-" + end;
        }
        Integer resolved = start != null ? start : end;
        return singular + " " + resolved;
    }

    private static Integer positiveInt(Object value) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return null;
    }
}
