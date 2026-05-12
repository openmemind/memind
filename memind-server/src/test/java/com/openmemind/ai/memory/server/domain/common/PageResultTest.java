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
package com.openmemind.ai.memory.server.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void convertsPageResponseToItemsAndPageMetadata() {
        PageResult<String> page = PageResult.from(new PageResponse<>(2, 20, 45, List.of("a")));

        assertThat(page.items()).containsExactly("a");
        assertThat(page.page().page()).isEqualTo(2);
        assertThat(page.page().pageSize()).isEqualTo(20);
        assertThat(page.page().totalItems()).isEqualTo(45);
        assertThat(page.page().totalPages()).isEqualTo(3);
        assertThat(page.page().hasPrevious()).isTrue();
        assertThat(page.page().hasNext()).isTrue();
    }

    @Test
    void reportsNoNextPageForLastPage() {
        PageResult<String> page = PageResult.from(new PageResponse<>(3, 20, 45, List.of("a")));

        assertThat(page.page().hasPrevious()).isTrue();
        assertThat(page.page().hasNext()).isFalse();
    }
}
