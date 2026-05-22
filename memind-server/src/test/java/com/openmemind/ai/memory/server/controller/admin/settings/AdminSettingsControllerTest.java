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
package com.openmemind.ai.memory.server.controller.admin.settings;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.server.configuration.RequestIdFilter;
import com.openmemind.ai.memory.server.domain.settings.UiPreferences;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import com.openmemind.ai.memory.server.service.settings.UiPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminSettingsControllerTest {

    private final StubUiPreferenceService service = new StubUiPreferenceService();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminSettingsController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void uiPreferencesCanBeReadAndUpdated() throws Exception {
        mockMvc.perform(get("/admin/v1/settings/ui-preferences"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.defaultTimeRange").value("7d"))
                .andExpect(jsonPath("$.data.theme").value("system"));

        mockMvc.perform(
                        put("/admin/v1/settings/ui-preferences")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "defaultTimeRange": "30d",
                                          "defaultMemoryView": "grid",
                                          "theme": "dark",
                                          "showOnboardingTips": false,
                                          "autoHideEmptyCollections": true
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data.defaultTimeRange").value("30d"))
                .andExpect(jsonPath("$.data.defaultMemoryView").value("grid"))
                .andExpect(jsonPath("$.data.theme").value("dark"));
    }

    private static final class StubUiPreferenceService extends UiPreferenceService {

        private UiPreferences preferences = UiPreferences.defaults();

        private StubUiPreferenceService() {
            super(null, null);
        }

        @Override
        public UiPreferences get() {
            return preferences;
        }

        @Override
        public UiPreferences update(UiPreferences preferences) {
            this.preferences = preferences;
            return preferences;
        }
    }
}
