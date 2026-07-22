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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.provider;

import com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure.MultiAiModelConfigurationException;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

public final class MultiAiModelProviderContext {

    private final Environment environment;
    private final ConfigurableListableBeanFactory beanFactory;

    public MultiAiModelProviderContext(
            Environment environment, ConfigurableListableBeanFactory beanFactory) {
        this.environment = environment;
        this.beanFactory = beanFactory;
    }

    public <T> T bind(String prefix, Class<T> type) {
        return Binder.get(environment)
                .bind(prefix, Bindable.of(type))
                .orElseGet(() -> newInstance(type));
    }

    public <T> ObjectProvider<T> provider(Class<T> type) {
        return beanFactory.getBeanProvider(type);
    }

    public ObservationRegistry observationRegistry() {
        return provider(ObservationRegistry.class).getIfUnique(() -> ObservationRegistry.NOOP);
    }

    public ToolCallingManager toolCallingManager(ObservationRegistry observationRegistry) {
        return provider(ToolCallingManager.class)
                .getIfUnique(
                        () ->
                                ToolCallingManager.builder()
                                        .observationRegistry(observationRegistry)
                                        .build());
    }

    private <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new MultiAiModelConfigurationException(
                    "Failed to instantiate configuration properties " + type.getName(), exception);
        }
    }
}
