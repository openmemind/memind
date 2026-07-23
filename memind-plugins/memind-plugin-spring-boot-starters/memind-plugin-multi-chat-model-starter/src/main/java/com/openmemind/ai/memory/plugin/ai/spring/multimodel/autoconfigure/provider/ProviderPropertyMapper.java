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

import org.springframework.beans.BeanUtils;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;

public final class ProviderPropertyMapper {

    private static final ConversionService CONVERSION_SERVICE =
            ApplicationConversionService.getSharedInstance();

    private ProviderPropertyMapper() {}

    public static void copyProperties(Object source, Object target, String... ignoredProperties) {
        BeanUtils.copyProperties(source, target, ignoredProperties);
    }

    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        return CONVERSION_SERVICE.convert(value, targetType);
    }
}
