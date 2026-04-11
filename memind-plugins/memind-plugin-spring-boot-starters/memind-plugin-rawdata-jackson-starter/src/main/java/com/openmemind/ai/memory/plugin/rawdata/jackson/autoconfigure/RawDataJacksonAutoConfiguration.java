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
package com.openmemind.ai.memory.plugin.rawdata.jackson.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentJackson;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.extraction.rawdata.content.RawContent;
import com.openmemind.ai.memory.core.plugin.CoreBuiltinRawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.utils.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@AutoConfiguration(
        afterName = {
            "org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
        })
@ConditionalOnClass({ObjectMapper.class, RawContent.class})
public class RawDataJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper rawDataObjectMapper(ObjectProvider<RawDataPlugin> rawDataPluginProvider) {
        ObjectMapper mapper = JsonUtils.mapper().copy();
        RawContentJackson.registerAll(mapper, collectRegistrars(rawDataPluginProvider));
        return mapper;
    }

    @Bean
    ServerHttpMessageConvertersCustomizer rawDataServerHttpMessageConvertersCustomizer(
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return builder ->
                builder.withJsonConverter(
                        new MappingJackson2HttpMessageConverter(objectMapperProvider.getObject()));
    }

    @Bean
    ClientHttpMessageConvertersCustomizer rawDataClientHttpMessageConvertersCustomizer(
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return builder ->
                builder.withJsonConverter(
                        new MappingJackson2HttpMessageConverter(objectMapperProvider.getObject()));
    }

    @Bean
    static BeanPostProcessor rawDataObjectMapperBeanPostProcessor(
            ObjectProvider<RawDataPlugin> rawDataPluginProvider) {
        return new RawDataObjectMapperBeanPostProcessor(rawDataPluginProvider);
    }

    private static List<RawContentTypeRegistrar> collectRegistrars(
            ObjectProvider<RawDataPlugin> rawDataPluginProvider) {
        List<RawContentTypeRegistrar> registrars =
                new ArrayList<>(new CoreBuiltinRawDataPlugin().typeRegistrars());
        rawDataPluginProvider
                .orderedStream()
                .forEach(plugin -> registrars.addAll(plugin.typeRegistrars()));
        return List.copyOf(registrars);
    }

    private static final class RawDataObjectMapperBeanPostProcessor implements BeanPostProcessor {

        private final ObjectProvider<RawDataPlugin> rawDataPluginProvider;

        private RawDataObjectMapperBeanPostProcessor(
                ObjectProvider<RawDataPlugin> rawDataPluginProvider) {
            this.rawDataPluginProvider = rawDataPluginProvider;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof ObjectMapper mapper) {
                RawContentJackson.registerAll(mapper, collectRegistrars(rawDataPluginProvider));
            }
            return bean;
        }
    }
}
