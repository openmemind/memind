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
package com.openmemind.ai.client.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmemind.ai.client.MemindClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemindClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MemindClientAutoConfiguration.class));

    @Test
    void autoConfiguration_withBaseUrl_createsBean() {
        contextRunner
                .withPropertyValues("memind.client.base-url=http://localhost:8366")
                .run(context -> assertThat(context).hasSingleBean(MemindClient.class));
    }

    @Test
    void autoConfiguration_withoutBaseUrl_doesNotCreateBean() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MemindClient.class));
    }

    @Test
    void autoConfiguration_withCustomBean_doesNotOverride() {
        contextRunner
                .withPropertyValues("memind.client.base-url=http://localhost:8366")
                .withBean(
                        MemindClient.class,
                        () -> MemindClient.builder().baseUrl("http://custom:9999").build())
                .run(context -> assertThat(context).hasSingleBean(MemindClient.class));
    }

    @Test
    void autoConfiguration_bindsAllProperties() {
        contextRunner
                .withPropertyValues(
                        "memind.client.base-url=http://localhost:8366",
                        "memind.client.api-token=mk-test",
                        "memind.client.connect-timeout=10s",
                        "memind.client.read-timeout=60s")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(MemindClient.class);
                            MemindClientProperties props =
                                    context.getBean(MemindClientProperties.class);
                            assertThat(props.getApiToken()).isEqualTo("mk-test");
                            assertThat(props.getConnectTimeout().getSeconds()).isEqualTo(10);
                            assertThat(props.getReadTimeout().getSeconds()).isEqualTo(60);
                        });
    }
}
