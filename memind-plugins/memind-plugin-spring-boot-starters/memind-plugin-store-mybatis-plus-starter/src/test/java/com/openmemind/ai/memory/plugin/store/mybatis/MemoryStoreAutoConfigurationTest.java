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
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.store.InMemoryMemoryStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.core.store.buffer.ConversationBuffer;
import com.openmemind.ai.memory.core.store.buffer.InsightBuffer;
import com.openmemind.ai.memory.core.store.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.textsearch.MemoryTextSearch;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.DefaultDBFieldHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.sqlite.SQLiteDataSource;
import reactor.core.publisher.Mono;

@DisplayName("Memory store auto-configuration")
class MemoryStoreAutoConfigurationTest {

    private ApplicationContextRunner newContextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MemoryMybatisPlusAutoConfiguration.class,
                                MybatisPlusAutoConfiguration.class))
                .withPropertyValues(
                        "memind.store.init-schema=false", "mybatis.lazy-initialization=true");
    }

    @Nested
    @DisplayName("Datasource boundary")
    class DatasourceBoundary {

        @Test
        @DisplayName("Create store beans only when an existing datasource is available")
        void createsStoreBeansWhenDataSourceExists() {
            newContextRunner()
                    .withUserConfiguration(ExistingDataSourceConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).hasSingleBean(DataSource.class);
                                assertThat(context).hasSingleBean(MemoryStore.class);
                                assertThat(context).hasSingleBean(MemoryBuffer.class);
                                MemoryStore memoryStore = context.getBean(MemoryStore.class);
                                assertThat(memoryStore.rawDataOperations()).isNotNull();
                                assertThat(memoryStore.itemOperations()).isNotNull();
                                assertThat(memoryStore.insightOperations()).isNotNull();
                                assertThat(context).doesNotHaveBean(InsightBuffer.class);
                                assertThat(context).doesNotHaveBean(ConversationBuffer.class);
                                assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                                assertThat(context).hasSingleBean(MetaObjectHandler.class);
                                assertThat(context).hasSingleBean(MemoryTextSearch.class);
                                assertThat(
                                                context.getBean(MemoryTextSearch.class)
                                                        .getClass()
                                                        .getName())
                                        .isEqualTo(
                                                "com.openmemind.ai.memory.plugin.store.mybatis.textsearch.sqlite.SqliteFtsTextSearch");
                            });
        }

        @Test
        @DisplayName("Back off completely when no datasource exists")
        void backsOffCompletelyWhenDataSourceMissing() {
            newContextRunner()
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                assertThat(context).doesNotHaveBean(DataSource.class);
                                assertThat(context).doesNotHaveBean(MemoryStore.class);
                                assertThat(context).doesNotHaveBean(InsightBuffer.class);
                                assertThat(context).doesNotHaveBean(ConversationBuffer.class);
                                assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
                                assertThat(context).doesNotHaveBean(MetaObjectHandler.class);
                                assertThat(context).doesNotHaveBean(MemoryTextSearch.class);
                            });
        }

        @Test
        @DisplayName(
                "Persist active conversation buffer and historical message count when schema is"
                        + " initialized")
        void persistsConversationBufferStateWhenSchemaInitialized() {
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MemorySchemaAutoConfiguration.class,
                                    MemoryMybatisPlusAutoConfiguration.class,
                                    MybatisPlusAutoConfiguration.class))
                    .withPropertyValues(
                            "memind.store.init-schema=true", "mybatis.lazy-initialization=true")
                    .withUserConfiguration(FileBackedDataSourceConfig.class)
                    .run(
                            context -> {
                                assertThat(context).hasNotFailed();
                                context.getBean(DdlApplicationRunner.class)
                                        .run(new DefaultApplicationArguments(new String[0]));
                                MemoryBuffer memoryBuffer = context.getBean(MemoryBuffer.class);
                                var conversationBufferStore =
                                        memoryBuffer.pendingConversationBuffer();
                                var memoryId = new DefaultMemoryId("u1", "a1");
                                String sessionId = memoryId.toIdentifier();

                                conversationBufferStore.save(
                                        sessionId,
                                        List.of(Message.user("hello"), Message.assistant("hi")));

                                assertThat(conversationBufferStore.load(sessionId))
                                        .extracting(Message::textContent)
                                        .containsExactly("hello", "hi");
                                assertThat(conversationBufferStore.loadMessageCount(sessionId))
                                        .isEqualTo(2);

                                conversationBufferStore.clear(sessionId);

                                assertThat(conversationBufferStore.load(sessionId)).isEmpty();
                                assertThat(conversationBufferStore.loadMessageCount(sessionId))
                                        .isEqualTo(2);

                                conversationBufferStore.save(
                                        sessionId, List.of(Message.user("next")));

                                assertThat(conversationBufferStore.load(sessionId))
                                        .extracting(Message::textContent)
                                        .containsExactly("next");
                                assertThat(conversationBufferStore.loadMessageCount(sessionId))
                                        .isEqualTo(3);
                                assertThat(conversationBufferStore.listActiveSessions(memoryId))
                                        .containsExactly(sessionId);
                            });
        }
    }

    @Nested
    @DisplayName("User bean override")
    class UserBeanOverride {

        private final ApplicationContextRunner contextRunner =
                newContextRunner().withUserConfiguration(CustomBeansConfig.class);

        @Test
        @DisplayName(
                "Back off when user provides MemoryStore, interceptor, and meta object handler")
        void backsOffForUserProvidedBeans() {
            contextRunner.run(
                    context -> {
                        assertThat(context).hasSingleBean(MemoryStore.class);
                        assertThat(context.getBean(MemoryStore.class))
                                .isSameAs(context.getBean("customMemoryStore"));

                        assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                        assertThat(context.getBean(MybatisPlusInterceptor.class))
                                .isSameAs(context.getBean("customMybatisPlusInterceptor"));

                        assertThat(context).hasSingleBean(MetaObjectHandler.class);
                        assertThat(context.getBean(MetaObjectHandler.class))
                                .isSameAs(context.getBean("customMetaObjectHandler"));

                        assertThat(context).hasSingleBean(MemoryTextSearch.class);
                        assertThat(context.getBean(MemoryTextSearch.class))
                                .isSameAs(context.getBean("customMemoryTextSearch"));
                        assertThat(context).doesNotHaveBean(InsightBuffer.class);
                        assertThat(context).doesNotHaveBean(ConversationBuffer.class);

                        assertThat(context).doesNotHaveBean(MybatisPlusMemoryStore.class);
                        assertThat(context.getBeansOfType(DefaultDBFieldHandler.class)).isEmpty();
                    });
        }
    }

    @Configuration
    static class ExistingDataSourceConfig {
        @Bean
        DataSource dataSource() {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite::memory:");
            return dataSource;
        }
    }

    @Configuration
    static class FileBackedDataSourceConfig {
        @Bean
        DataSource dataSource() throws IOException {
            Path dbPath = Files.createTempFile("memind-mybatis-plus-", ".db");
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + dbPath);
            return dataSource;
        }
    }

    @Configuration
    @Import(ExistingDataSourceConfig.class)
    static class CustomBeansConfig {

        @Bean
        MemoryStore customMemoryStore() {
            return new InMemoryMemoryStore();
        }

        @Bean
        MybatisPlusInterceptor customMybatisPlusInterceptor() {
            return new MybatisPlusInterceptor();
        }

        @Bean
        MetaObjectHandler customMetaObjectHandler() {
            return new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject metaObject) {}

                @Override
                public void updateFill(MetaObject metaObject) {}
            };
        }

        @Bean
        MemoryTextSearch customMemoryTextSearch() {
            return (memoryId, query, topK, target) -> Mono.just(List.of());
        }
    }
}
