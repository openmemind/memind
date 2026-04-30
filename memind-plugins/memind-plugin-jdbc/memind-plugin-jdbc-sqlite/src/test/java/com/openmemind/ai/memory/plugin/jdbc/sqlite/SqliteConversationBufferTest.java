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
package com.openmemind.ai.memory.plugin.jdbc.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteConversationBufferTest {

    @Test
    void pendingAndRecentViewsShareOnePersistedConversationLog(@TempDir Path tempDir) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("conversation.db"));

        var pendingBuffer = new SqliteConversationBuffer(dataSource, true);
        var recentBuffer = new SqliteRecentConversationBuffer(dataSource, true);
        String sessionId = "user-1:agent-1";

        pendingBuffer.append(
                sessionId, Message.user("hello", Instant.parse("2026-04-01T00:00:00Z")));
        pendingBuffer.append(
                sessionId, Message.assistant("hi", Instant.parse("2026-04-01T00:00:01Z")));

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
    }
}
