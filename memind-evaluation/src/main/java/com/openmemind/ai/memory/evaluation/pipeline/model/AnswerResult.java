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
package com.openmemind.ai.memory.evaluation.pipeline.model;

/**
 * Single question answer result, including question, standard answer, generated answer, and formatted context
 *
 */
public record AnswerResult(
        String questionId,
        String question,
        String goldenAnswer,
        String generatedAnswer,
        String category,
        String conversationId,
        String formattedContext) {}
