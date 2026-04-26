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
package com.openmemind.ai.memory.server.mapper.memorythread;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.openmemind.ai.memory.plugin.store.mybatis.dataobject.MemoryThreadMembershipDO;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadMembershipMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryThreadProjectionMapper;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MybatisAdminMemoryThreadQueryMapperTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "test-mapper"),
                MemoryThreadMembershipDO.class);
    }

    @Test
    void findItemsByThreadKeyOrdersByPrimaryThenWeightThenItemId() {
        CapturingMembershipMapper membershipMapper = new CapturingMembershipMapper();
        MybatisAdminMemoryThreadQueryMapper mapper =
                new MybatisAdminMemoryThreadQueryMapper(
                        noopProjectionMapper(), membershipMapper.proxy());

        mapper.findItemsByThreadKey("u1:a1", "topic:topic:concept:travel");

        String sqlSegment =
                membershipMapper.capturedWrapper().getSqlSegment().toLowerCase(Locale.ROOT);
        String orderBySegment = sqlSegment.substring(sqlSegment.indexOf("order by"));

        assertThat(orderBySegment).contains("order by");
        assertThat(orderBySegment).contains("is_primary");
        assertThat(orderBySegment).contains("relevance_weight");
        assertThat(orderBySegment).contains("item_id");
        assertThat(orderBySegment).contains("id");
        assertThat(orderBySegment.indexOf("is_primary"))
                .isLessThan(orderBySegment.indexOf("relevance_weight"));
        assertThat(orderBySegment.indexOf("relevance_weight"))
                .isLessThan(orderBySegment.indexOf("item_id"));
        assertThat(orderBySegment.lastIndexOf("item_id"))
                .isLessThan(orderBySegment.lastIndexOf("id"));
    }

    private static MemoryThreadProjectionMapper noopProjectionMapper() {
        return (MemoryThreadProjectionMapper)
                Proxy.newProxyInstance(
                        MemoryThreadProjectionMapper.class.getClassLoader(),
                        new Class<?>[] {MemoryThreadProjectionMapper.class},
                        (proxy, method, args) -> {
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private static final class CapturingMembershipMapper {

        private LambdaQueryWrapper<MemoryThreadMembershipDO> capturedWrapper;

        private MemoryThreadMembershipMapper proxy() {
            return (MemoryThreadMembershipMapper)
                    Proxy.newProxyInstance(
                            MemoryThreadMembershipMapper.class.getClassLoader(),
                            new Class<?>[] {MemoryThreadMembershipMapper.class},
                            (proxy, method, args) -> {
                                if ("selectList".equals(method.getName())) {
                                    capturedWrapper =
                                            (LambdaQueryWrapper<MemoryThreadMembershipDO>) args[0];
                                    return List.of();
                                }
                                throw new UnsupportedOperationException(method.getName());
                            });
        }

        private LambdaQueryWrapper<MemoryThreadMembershipDO> capturedWrapper() {
            return capturedWrapper;
        }
    }
}
