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
package com.openmemind.ai.memory.plugin.ai.spring.multimodel.autoconfigure;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

public final class MultiAiModelConfigurationDetector {

    private MultiAiModelConfigurationDetector() {}

    public static boolean hasConfiguredModels(Environment environment, String prefix) {
        try {
            return Binder.get(environment)
                    .bind(prefix, Bindable.mapOf(String.class, Object.class))
                    .map(models -> !models.isEmpty())
                    .orElse(false);
        } catch (BindException exception) {
            return true;
        }
    }
}
