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

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import com.baomidou.mybatisplus.extension.parser.cache.JdkSerialCaffeineJsqlParseCache;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openmemind.ai.memory.core.extraction.insight.buffer.InsightBufferStore;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.plugin.store.mybatis.handler.DefaultDBFieldHandler;
import com.openmemind.ai.memory.plugin.store.mybatis.initializer.DefaultTaxonomySeeder;
import com.openmemind.ai.memory.plugin.store.mybatis.initializer.MemoryStoreProperties;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.InsightBufferMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryInsightTypeMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryItemMapper;
import com.openmemind.ai.memory.plugin.store.mybatis.mapper.MemoryRawDataMapper;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@AutoConfiguration(
        before =
                MybatisPlusAutoConfiguration
                        .class) // Purpose: to be configured before MyBatis Plus auto-configuration
// to avoid @MapperScan possibly not scanning Mapper and printing
// warning logs
@MapperScan(
        value = "com.openmemind.ai.memory.plugin.store.mybatis.mapper",
        annotationClass = Mapper.class,
        lazyInitialization = "${mybatis.lazy-initialization:false}",
        nameGenerator =
                FullyQualifiedAnnotationBeanNameGenerator
                        .class) // Mapper lazy loading, currently only used for unit testing
@EnableConfigurationProperties(MemoryStoreProperties.class)
public class MemoryMybatisPlusAutoConfiguration {

    static {
        // Dynamic SQL intelligent optimization supports local cache to accelerate parsing, more
        // complete tenant complex XML dynamic SQL support, static injection cache
        JsqlParserGlobal.setJsqlParseCache(
                new JdkSerialCaffeineJsqlParseCache(
                        (cache) -> cache.maximumSize(1024).expireAfterWrite(5, TimeUnit.SECONDS)));

        // Register JavaTimeModule to ensure that Java 8 time types such as Instant can be
        // serialized
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        JacksonTypeHandler.setObjectMapper(mapper);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(
                new PaginationInnerInterceptor()); // Pagination plugin
        // ↓↓↓ Enable as needed, may affect places like updateBatch: for example, file configuration
        // management ↓↓↓
        // mybatisPlusInterceptor.addInnerInterceptor(new BlockAttackInnerInterceptor()); //
        // Intercept update and delete statements without specified conditions
        return mybatisPlusInterceptor;
    }

    @Bean
    public MetaObjectHandler defaultMetaObjectHandler() {
        return new DefaultDBFieldHandler(); // Automatic fill parameter class
    }

    @Bean
    public MybatisPlusMemoryStore mybatisPlusMemoryStore(
            MemoryRawDataMapper rawDataMapper,
            MemoryItemMapper itemMapper,
            MemoryInsightTypeMapper insightTypeMapper,
            MemoryInsightMapper insightMapper) {
        return new MybatisPlusMemoryStore(
                rawDataMapper, itemMapper, insightTypeMapper, insightMapper);
    }

    @Bean
    @ConditionalOnMissingBean(InsightBufferStore.class)
    public InsightBufferStore insightBufferStore(InsightBufferMapper insightBufferMapper) {
        return new MybatisPlusInsightBufferStore(insightBufferMapper);
    }

    @Bean
    @ConditionalOnMissingBean(DefaultTaxonomySeeder.class)
    @ConditionalOnProperty(
            name = "memind.store.init-schema",
            havingValue = "true",
            matchIfMissing = true)
    public DefaultTaxonomySeeder defaultTaxonomySeeder(MemoryStore memoryStore) {
        return new DefaultTaxonomySeeder(memoryStore);
    }
}
