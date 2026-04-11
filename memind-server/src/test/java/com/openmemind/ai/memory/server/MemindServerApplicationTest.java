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
package com.openmemind.ai.memory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.llm.StructuredChatClient;
import com.openmemind.ai.memory.core.vector.MemoryVector;
import com.openmemind.ai.memory.server.runtime.MemoryRuntimeManager;
import com.openmemind.ai.memory.server.support.NoopRuntimeTestConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {MemindServerApplication.class, NoopRuntimeTestConfiguration.class})
class MemindServerApplicationTest {

    private static final Path DB_PATH =
            Path.of("target", "memind-server-application-" + UUID.randomUUID() + ".db")
                    .toAbsolutePath();

    static {
        try {
            Files.createDirectories(DB_PATH.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to prepare application-test database path", exception);
        }
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private MemoryRuntimeManager runtimeManager;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.main.web-application-type", () -> "servlet");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("memind.store.init-schema", () -> "true");
        registry.add(
                "spring.autoconfigure.exclude",
                () -> NoopRuntimeTestConfiguration.SPRING_AI_AUTOCONFIG_EXCLUDES);
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterAll
    static void cleanUpDatabase() throws IOException {
        Files.deleteIfExists(DB_PATH);
    }

    @Test
    void contextLoadsWithRuntimeDependencies() {
        assertThat(applicationContext.getBeanProvider(StructuredChatClient.class).getIfAvailable())
                .isNotNull();
        assertThat(applicationContext.getBeanProvider(MemoryVector.class).getIfAvailable())
                .isNotNull();

        try (var lease = runtimeManager.acquire()) {
            assertThat(lease.handle().memory()).isNotNull();
            assertThat(lease.handle().options()).isNotNull();
        }
    }

    @Test
    void commitApiAcceptsRequestWhenRuntimeIsAvailable() throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/commit")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "userId": "u1",
                                          "agentId": "a1"
                                        }
                                        """))
                .andExpect(status().isOk());
    }

    @Test
    void extractApiAcceptsBuiltinAndDocumentRawContentViaApplicationObjectMapper()
            throws Exception {
        mockMvc.perform(
                        post("/open/v1/memory/extract")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "userId": "u1",
                                          "agentId": "a1",
                                          "rawContent": {
                                            "type": "conversation",
                                            "messages": [
                                              {
                                                "role": "USER",
                                                "content": [
                                                  {
                                                    "type": "text",
                                                    "text": "hello"
                                                  }
                                                ],
                                                "timestamp": "2026-03-31T10:00:00Z"
                                              }
                                            ]
                                          }
                                        }
                                        """))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/open/v1/memory/extract")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "userId": "u1",
                                          "agentId": "a1",
                                          "rawContent": {
                                            "type": "document",
                                            "title": "Guide",
                                            "mimeType": "text/plain",
                                            "parsedText": "hello",
                                            "sections": [],
                                            "metadata": {}
                                          }
                                        }
                                        """))
                .andExpect(status().isOk());
    }
}
