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
package com.openmemind.ai.memory.plugin.store.mybatis.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.BaseDO;
import java.time.Instant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultDBFieldHandler")
class DefaultDBFieldHandlerTest {

    private final DefaultDBFieldHandler handler = new DefaultDBFieldHandler();

    @Test
    @DisplayName("Refresh updatedAt on every update fill")
    void refreshesUpdatedAtOnEveryUpdateFill() {
        TestBaseDO dataObject = new TestBaseDO();
        Instant original = Instant.parse("2026-03-20T00:00:00Z");
        dataObject.setUpdatedAt(original);

        handler.updateFill(SystemMetaObject.forObject(dataObject));

        assertThat(dataObject.getUpdatedAt()).isAfter(original);
    }

    private static final class TestBaseDO extends BaseDO {}
}
