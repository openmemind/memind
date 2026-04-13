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
package com.openmemind.ai.memory.example.java.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.example.java.support.ExampleDataLoader;
import org.junit.jupiter.api.Test;

class DocumentMemoryExampleTest {

    @Test
    void defaultDocumentsDescribePaymentsKnowledgePack() {
        assertThat(DocumentMemoryExample.defaultDocuments())
                .extracting(
                        DocumentMemoryExample.ExampleDocument::relativePath,
                        DocumentMemoryExample.ExampleDocument::role)
                .containsExactly(
                        tuple("document/release-readiness.md", "release readiness packet"),
                        tuple("document/migration-runbook.md", "migration runbook"),
                        tuple("document/incident-retrospective.html", "incident retrospective"),
                        tuple("document/service-ownership.csv", "service ownership matrix"),
                        tuple("document/weekend-handoff.txt", "weekend release handoff"));
    }

    @Test
    void defaultQueriesMixSimpleAndDeepCrossDocumentQuestions() {
        assertThat(DocumentMemoryExample.defaultQueryCases())
                .extracting(
                        DocumentMemoryExample.ExampleQueryCase::title,
                        DocumentMemoryExample.ExampleQueryCase::strategy)
                .containsExactly(
                        tuple(
                                "Migration Window And Preconditions",
                                RetrievalConfig.Strategy.SIMPLE),
                        tuple("429 Escalation And Observability", RetrievalConfig.Strategy.SIMPLE),
                        tuple("Rollback Decision Path", RetrievalConfig.Strategy.SIMPLE),
                        tuple("Cross-Document Operational Summary", RetrievalConfig.Strategy.DEEP));
    }

    @Test
    void bundledKnowledgePackFilesExistAndAreReadable() {
        var loader = new ExampleDataLoader();

        assertThat(DocumentMemoryExample.defaultDocuments())
                .allSatisfy(
                        document ->
                                assertThat(loader.loadBytes(document.relativePath()))
                                        .as(document.relativePath())
                                        .isNotEmpty());
    }

    @Test
    void mimeTypeForSupportsTheKnowledgePackFormats() {
        assertThat(DocumentMemoryExample.mimeTypeFor("document/release-readiness.md"))
                .isEqualTo("text/markdown");
        assertThat(DocumentMemoryExample.mimeTypeFor("document/incident-retrospective.html"))
                .isEqualTo("text/html");
        assertThat(DocumentMemoryExample.mimeTypeFor("document/service-ownership.csv"))
                .isEqualTo("text/csv");
        assertThat(DocumentMemoryExample.mimeTypeFor("document/weekend-handoff.txt"))
                .isEqualTo("text/plain");
    }
}
