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
import com.openmemind.ai.memory.core.buffer.MemoryBuffer;
import com.openmemind.ai.memory.core.buffer.PendingConversationBuffer;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.ConversationBufferMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.schema.MemorySchemaAutoConfiguration;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
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

    @Test
    void autoConfiguredPendingBufferDrainsPreexistingBatchOnceConcurrently() {
        contextRunner
                .withUserConfiguration(BlockingConversationBufferMapperConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            context.getBean(DdlApplicationRunner.class)
                                    .run(new DefaultApplicationArguments(new String[0]));

                            MemoryBuffer memoryBuffer = context.getBean(MemoryBuffer.class);
                            PendingConversationBuffer pendingBuffer =
                                    memoryBuffer.pendingConversationBuffer();
                            String sessionId = "mybatis-concurrent-user:agent";
                            int messageCount = 20;
                            for (int i = 0; i < messageCount; i++) {
                                pendingBuffer.append(
                                        sessionId,
                                        Message.user(
                                                        "message-" + i,
                                                        Instant.parse("2026-04-01T00:00:00Z")
                                                                .plusSeconds(i))
                                                .withSourceClient("web"));
                            }

                            List<List<Message>> drained =
                                    drainConcurrently(pendingBuffer, pendingBuffer, sessionId);

                            assertAtomicDrainResult(drained, messageCount);
                            assertThat(pendingBuffer.load(sessionId)).isEmpty();
                            assertThat(
                                            memoryBuffer
                                                    .recentConversationBuffer()
                                                    .loadRecent(sessionId, 100))
                                    .extracting(Message::textContent)
                                    .containsExactlyElementsOf(expectedMessages(messageCount));
                        });
    }

    private static List<List<Message>> drainConcurrently(
            PendingConversationBuffer first, PendingConversationBuffer second, String sessionId)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        try {
            Callable<List<Message>> firstDrain =
                    () -> drainAfterBarrier(first, sessionId, startBarrier);
            Callable<List<Message>> secondDrain =
                    () -> drainAfterBarrier(second, sessionId, startBarrier);
            Future<List<Message>> firstResult = executor.submit(firstDrain);
            Future<List<Message>> secondResult = executor.submit(secondDrain);
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Message> drainAfterBarrier(
            PendingConversationBuffer buffer, String sessionId, CyclicBarrier startBarrier) {
        awaitBarrier(startBarrier);
        return buffer.drain(sessionId);
    }

    private static void assertAtomicDrainResult(List<List<Message>> drained, int messageCount) {
        List<Message> combined = drained.stream().flatMap(List::stream).toList();

        assertThat(combined).hasSize(messageCount);
        assertThat(combined)
                .extracting(Message::textContent)
                .containsExactlyInAnyOrderElementsOf(expectedMessages(messageCount));
        assertThat(combined).extracting(Message::sourceClient).containsOnly("web");
        assertThat(drained.stream().map(List::size).sorted().toList())
                .containsExactly(0, messageCount);
    }

    private static List<String> expectedMessages(int messageCount) {
        List<String> expected = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            expected.add("message-" + i);
        }
        return expected;
    }

    @Configuration(proxyBeanMethods = false)
    static class FileBackedDataSourceConfig {

        @Bean
        DataSource dataSource() throws IOException {
            Path dbPath = Files.createTempFile("memind-mybatis-conversation-", ".db");
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + dbPath + "?busy_timeout=5000");
            return dataSource;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BlockingConversationBufferMapperConfig {

        @Bean
        BeanPostProcessor blockingConversationBufferMapper() {
            return new BlockingConversationBufferMapperPostProcessor();
        }
    }

    private static final class BlockingConversationBufferMapperPostProcessor
            implements BeanPostProcessor {

        private final CyclicBarrier afterBothSelections = new CyclicBarrier(2);

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName)
                throws BeansException {
            if (!(bean instanceof ConversationBufferMapper mapper)) {
                return bean;
            }
            return Proxy.newProxyInstance(
                    ConversationBufferMapper.class.getClassLoader(),
                    new Class<?>[] {ConversationBufferMapper.class},
                    (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(mapper, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                        if ("selectPendingRowsBySessionId".equals(method.getName())
                                && result instanceof List<?> rows
                                && !rows.isEmpty()) {
                            awaitBarrier(afterBothSelections);
                        }
                        return result;
                    });
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating drain race", ex);
        } catch (BrokenBarrierException ex) {
            throw new IllegalStateException("Failed to coordinate drain race", ex);
        }
    }
}
