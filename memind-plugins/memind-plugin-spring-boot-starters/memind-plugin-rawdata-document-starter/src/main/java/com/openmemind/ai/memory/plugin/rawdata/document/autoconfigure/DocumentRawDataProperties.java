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
package com.openmemind.ai.memory.plugin.rawdata.document.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.rawdata.document")
public class DocumentRawDataProperties {

    private boolean enabled = true;
    private boolean nativeTextEnabled = true;
    private boolean tikaEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNativeTextEnabled() {
        return nativeTextEnabled;
    }

    public void setNativeTextEnabled(boolean nativeTextEnabled) {
        this.nativeTextEnabled = nativeTextEnabled;
    }

    public boolean isTikaEnabled() {
        return tikaEnabled;
    }

    public void setTikaEnabled(boolean tikaEnabled) {
        this.tikaEnabled = tikaEnabled;
    }
}
