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
package com.openmemind.ai.memory.plugin.store.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

class MybatisPlusConversationBufferTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemorySchemaAutoConfiguration.class,
                                    MemoryMybatisPlusAutoConfiguration.class,
                                    MybatisPlusAutoConfiguration.class))
                    .withPropertyValues(
                            "memind.store.init-schema=true", "mybatis.lazy-initialization=true")
                    .withUserConfiguration(FileBackedDataSourceConfig.class);

    @Test
    void pendingAndRecentViewsShareOnePersistedConversationLog() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(DdlApplicationRunner.class)
                            .run(new DefaultApplicationArguments(new String[0]));

                    ConversationBufferMapper mapper =
                            context.getBean(ConversationBufferMapper.class);
                    var pendingBuffer = new MybatisPlusConversationBuffer(mapper);
                    var recentBuffer = new MybatisPlusRecentConversationBuffer(mapper);
                    String sessionId = "user-1:agent-1";

                    pendingBuffer.append(
                            sessionId,
                            Message.user("hello", Instant.parse("2026-04-01T00:00:00Z")));
                    pendingBuffer.append(
                            sessionId,
                            Message.assistant("hi", Instant.parse("2026-04-01T00:00:01Z")));

                    assertThat(pendingBuffer.load(sessionId))
                            .extracting(Message::textContent)
                            .containsExactly("hello", "hi");
                    assertThat(recentBuffer.loadRecent(sessionId, 10))
                            .extracting(Message::textContent)
                            .containsExactly("hello", "hi");

                    assertThat(pendingBuffer.drain(sessionId))
                            .extracting(Message::textContent)
                            .containsExactly("hello", "hi");

                    assertThat(pendingBuffer.load(sessionId)).isEmpty();
                    assertThat(recentBuffer.loadRecent(sessionId, 10))
                            .extracting(Message::textContent)
                            .containsExactly("hello", "hi");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class FileBackedDataSourceConfig {

        @Bean
        DataSource dataSource() throws IOException {
            Path dbPath = Files.createTempFile("memind-mybatis-conversation-", ".db");
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + dbPath);
            return dataSource;
        }
    }
}
