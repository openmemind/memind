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
package com.openmemind.ai.memory.plugin.rawdata.toolcall.autoconfigure;

import com.openmemind.ai.memory.plugin.rawdata.toolcall.config.ToolCallChunkingOptions;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.rawdata.toolcall")
public class ToolCallRawDataProperties {

    private static final ToolCallChunkingOptions DEFAULT_CHUNKING =
            ToolCallChunkingOptions.defaults();

    private boolean enabled = true;
    private final ToolCallChunkingProperties chunking = new ToolCallChunkingProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ToolCallChunkingProperties getChunking() {
        return chunking;
    }

    public ToolCallChunkingOptions chunkingOptions() {
        return chunking.toOptions();
    }

    public static final class ToolCallChunkingProperties {

        private int targetTokens = DEFAULT_CHUNKING.targetTokens();
        private int hardMaxTokens = DEFAULT_CHUNKING.hardMaxTokens();
        private Duration maxTimeWindow = DEFAULT_CHUNKING.maxTimeWindow();

        public int getTargetTokens() {
            return targetTokens;
        }

        public void setTargetTokens(int targetTokens) {
            this.targetTokens = targetTokens;
        }

        public int getHardMaxTokens() {
            return hardMaxTokens;
        }

        public void setHardMaxTokens(int hardMaxTokens) {
            this.hardMaxTokens = hardMaxTokens;
        }

        public Duration getMaxTimeWindow() {
            return maxTimeWindow;
        }

        public void setMaxTimeWindow(Duration maxTimeWindow) {
            this.maxTimeWindow = maxTimeWindow;
        }

        ToolCallChunkingOptions toOptions() {
            return new ToolCallChunkingOptions(targetTokens, hardMaxTokens, maxTimeWindow);
        }
    }
}
