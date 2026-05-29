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
package com.openmemind.ai.memory.plugin.jdbc.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.plugin.jdbc.internal.buffer.ConversationBufferRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MysqlConversationBufferConcurrencyTest {

    @Container private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void concurrentDrainClaimsPreexistingBatchOnce() throws Exception {
        DataSource dataSource = dataSource();
        String sessionId = "mysql-concurrent-user:agent";
        int messageCount = 20;
        MysqlConversationBuffer seedBuffer = new MysqlConversationBuffer(dataSource);
        for (int i = 0; i < messageCount; i++) {
            seedBuffer.append(
                    sessionId,
                    Message.user(
                            "message-" + i, Instant.parse("2026-04-01T00:00:00Z").plusSeconds(i)));
        }

        CyclicBarrier afterBothSelections = new CyclicBarrier(2);
        // Regression trap for the old select-then-mark drain path.
        var first =
                new MysqlConversationBuffer(
                        new BlockingSelectAccessor(dataSource, afterBothSelections));
        var second =
                new MysqlConversationBuffer(
                        new BlockingSelectAccessor(dataSource, afterBothSelections));

        List<List<Message>> drained = drainConcurrently(first, second, sessionId);

        assertAtomicDrainResult(drained, messageCount);
        assertThat(seedBuffer.load(sessionId)).isEmpty();
        assertThat(new MysqlRecentConversationBuffer(dataSource).loadRecent(sessionId, 100))
                .extracting(Message::textContent)
                .containsExactlyElementsOf(expectedMessages(messageCount));
    }

    private static List<List<Message>> drainConcurrently(
            MysqlConversationBuffer first, MysqlConversationBuffer second, String sessionId)
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
            MysqlConversationBuffer buffer, String sessionId, CyclicBarrier startBarrier) {
        awaitBarrier(startBarrier);
        return buffer.drain(sessionId);
    }

    private static void assertAtomicDrainResult(List<List<Message>> drained, int messageCount) {
        List<Message> combined = drained.stream().flatMap(List::stream).toList();

        assertThat(combined).hasSize(messageCount);
        assertThat(combined)
                .extracting(Message::textContent)
                .containsExactlyInAnyOrderElementsOf(expectedMessages(messageCount));
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

    private static DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }

    private static final class BlockingSelectAccessor extends MysqlConversationBufferAccessor {

        private final CyclicBarrier afterBothSelections;

        private BlockingSelectAccessor(DataSource dataSource, CyclicBarrier afterBothSelections) {
            super(dataSource);
            this.afterBothSelections = afterBothSelections;
        }

        @Override
        public List<ConversationBufferRow> selectPending(String sessionId) {
            List<ConversationBufferRow> rows = super.selectPending(sessionId);
            if (rows.isEmpty()) {
                return rows;
            }
            awaitBothSelections();
            return rows.stream()
                    .sorted(Comparator.comparingLong(ConversationBufferRow::id))
                    .toList();
        }

        private void awaitBothSelections() {
            awaitBarrier(afterBothSelections);
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
