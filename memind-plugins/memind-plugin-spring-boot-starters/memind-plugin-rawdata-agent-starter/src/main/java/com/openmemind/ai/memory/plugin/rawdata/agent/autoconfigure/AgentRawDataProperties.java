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
package com.openmemind.ai.memory.plugin.rawdata.agent.autoconfigure;

import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentPrivacyOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentRawDataOptions;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memind.rawdata.agent")
public class AgentRawDataProperties {

    private static final AgentRawDataOptions DEFAULTS = AgentRawDataOptions.defaults();

    private boolean enabled = true;
    private final AgentChunkingProperties chunking = new AgentChunkingProperties();
    private final AgentExtractionProperties extraction = new AgentExtractionProperties();
    private final AgentPrivacyProperties privacy = new AgentPrivacyProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AgentChunkingProperties getChunking() {
        return chunking;
    }

    public AgentExtractionProperties getExtraction() {
        return extraction;
    }

    public AgentPrivacyProperties getPrivacy() {
        return privacy;
    }

    public AgentRawDataOptions toOptions() {
        return new AgentRawDataOptions(
                chunking.toOptions(), privacy.toOptions(), extraction.toOptions());
    }

    public static final class AgentChunkingProperties {

        private int targetEpisodeTokens = DEFAULTS.chunking().targetEpisodeTokens();
        private int hardMaxTokens = DEFAULTS.chunking().hardMaxTokens();
        private int maxEventsPerEpisode = DEFAULTS.chunking().maxEventsPerEpisode();
        private Duration maxEventGap = DEFAULTS.chunking().maxEventGap();

        public int getTargetEpisodeTokens() {
            return targetEpisodeTokens;
        }

        public void setTargetEpisodeTokens(int targetEpisodeTokens) {
            this.targetEpisodeTokens = targetEpisodeTokens;
        }

        public int getHardMaxTokens() {
            return hardMaxTokens;
        }

        public void setHardMaxTokens(int hardMaxTokens) {
            this.hardMaxTokens = hardMaxTokens;
        }

        public int getMaxEventsPerEpisode() {
            return maxEventsPerEpisode;
        }

        public void setMaxEventsPerEpisode(int maxEventsPerEpisode) {
            this.maxEventsPerEpisode = maxEventsPerEpisode;
        }

        public Duration getMaxEventGap() {
            return maxEventGap;
        }

        public void setMaxEventGap(Duration maxEventGap) {
            this.maxEventGap = maxEventGap;
        }

        AgentChunkingOptions toOptions() {
            return new AgentChunkingOptions(
                    targetEpisodeTokens, hardMaxTokens, maxEventsPerEpisode, maxEventGap);
        }
    }

    public static final class AgentExtractionProperties {

        private boolean extractTool = DEFAULTS.extraction().extractTool();
        private boolean extractResolution = DEFAULTS.extraction().extractResolution();
        private boolean extractPlaybook = DEFAULTS.extraction().extractPlaybook();
        private boolean extractDirective = DEFAULTS.extraction().extractDirective();
        private boolean extractOnEveryTool = DEFAULTS.extraction().extractOnEveryTool();
        private int minEventsForExtraction = DEFAULTS.extraction().minEventsForExtraction();
        private int minEventsForPlaybook = DEFAULTS.extraction().minEventsForPlaybook();
        private boolean requireSuccessForPlaybook =
                DEFAULTS.extraction().requireSuccessForPlaybook();

        public boolean isExtractTool() {
            return extractTool;
        }

        public void setExtractTool(boolean extractTool) {
            this.extractTool = extractTool;
        }

        public boolean isExtractResolution() {
            return extractResolution;
        }

        public void setExtractResolution(boolean extractResolution) {
            this.extractResolution = extractResolution;
        }

        public boolean isExtractPlaybook() {
            return extractPlaybook;
        }

        public void setExtractPlaybook(boolean extractPlaybook) {
            this.extractPlaybook = extractPlaybook;
        }

        public boolean isExtractDirective() {
            return extractDirective;
        }

        public void setExtractDirective(boolean extractDirective) {
            this.extractDirective = extractDirective;
        }

        public boolean isExtractOnEveryTool() {
            return extractOnEveryTool;
        }

        public void setExtractOnEveryTool(boolean extractOnEveryTool) {
            this.extractOnEveryTool = extractOnEveryTool;
        }

        public int getMinEventsForExtraction() {
            return minEventsForExtraction;
        }

        public void setMinEventsForExtraction(int minEventsForExtraction) {
            this.minEventsForExtraction = minEventsForExtraction;
        }

        public int getMinEventsForPlaybook() {
            return minEventsForPlaybook;
        }

        public void setMinEventsForPlaybook(int minEventsForPlaybook) {
            this.minEventsForPlaybook = minEventsForPlaybook;
        }

        public boolean isRequireSuccessForPlaybook() {
            return requireSuccessForPlaybook;
        }

        public void setRequireSuccessForPlaybook(boolean requireSuccessForPlaybook) {
            this.requireSuccessForPlaybook = requireSuccessForPlaybook;
        }

        AgentExtractionOptions toOptions() {
            return new AgentExtractionOptions(
                    extractTool,
                    extractResolution,
                    extractPlaybook,
                    extractDirective,
                    extractOnEveryTool,
                    minEventsForExtraction,
                    minEventsForPlaybook,
                    requireSuccessForPlaybook);
        }
    }

    public static final class AgentPrivacyProperties {

        private boolean redactSecrets = DEFAULTS.privacy().redactSecrets();
        private int maxInputChars = DEFAULTS.privacy().maxInputChars();
        private int maxOutputChars = DEFAULTS.privacy().maxOutputChars();
        private boolean captureFileContent = DEFAULTS.privacy().captureFileContent();
        private List<String> denyPathPatterns = DEFAULTS.privacy().denyPathPatterns();
        private List<String> allowPathPatterns = DEFAULTS.privacy().allowPathPatterns();

        public boolean isRedactSecrets() {
            return redactSecrets;
        }

        public void setRedactSecrets(boolean redactSecrets) {
            this.redactSecrets = redactSecrets;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
        }

        public int getMaxOutputChars() {
            return maxOutputChars;
        }

        public void setMaxOutputChars(int maxOutputChars) {
            this.maxOutputChars = maxOutputChars;
        }

        public boolean isCaptureFileContent() {
            return captureFileContent;
        }

        public void setCaptureFileContent(boolean captureFileContent) {
            this.captureFileContent = captureFileContent;
        }

        public List<String> getDenyPathPatterns() {
            return denyPathPatterns;
        }

        public void setDenyPathPatterns(List<String> denyPathPatterns) {
            this.denyPathPatterns = denyPathPatterns;
        }

        public List<String> getAllowPathPatterns() {
            return allowPathPatterns;
        }

        public void setAllowPathPatterns(List<String> allowPathPatterns) {
            this.allowPathPatterns = allowPathPatterns;
        }

        AgentPrivacyOptions toOptions() {
            return new AgentPrivacyOptions(
                    redactSecrets,
                    maxInputChars,
                    maxOutputChars,
                    captureFileContent,
                    denyPathPatterns,
                    allowPathPatterns);
        }
    }
}
