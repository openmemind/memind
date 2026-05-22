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

import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.domain.settings.UiPreferences;
import com.openmemind.ai.memory.server.service.settings.UiPreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/settings")
public class AdminSettingsController {

    private final UiPreferenceService uiPreferenceService;

    public AdminSettingsController(UiPreferenceService uiPreferenceService) {
        this.uiPreferenceService = uiPreferenceService;
    }

    @GetMapping("/ui-preferences")
    public SuccessResult<UiPreferences> uiPreferences() {
        return new SuccessResult<>(uiPreferenceService.get());
    }

    @PutMapping("/ui-preferences")
    public SuccessResult<UiPreferences> updateUiPreferences(
            @Valid @RequestBody UiPreferences preferences) {
        return new SuccessResult<>(uiPreferenceService.update(preferences));
    }
}
