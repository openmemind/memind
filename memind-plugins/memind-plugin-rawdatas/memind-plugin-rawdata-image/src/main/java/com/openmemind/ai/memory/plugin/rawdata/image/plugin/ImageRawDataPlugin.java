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
package com.openmemind.ai.memory.plugin.rawdata.image.plugin;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.plugin.rawdata.image.chunk.ImageSegmentComposer;
import com.openmemind.ai.memory.plugin.rawdata.image.config.ImageExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.image.processor.ImageContentProcessor;
import java.util.List;
import java.util.Set;

public final class ImageRawDataPlugin implements RawDataPlugin {

    private final ImageExtractionOptions options;

    public ImageRawDataPlugin() {
        this(ImageExtractionOptions.defaults());
    }

    public ImageRawDataPlugin(ImageExtractionOptions options) {
        this.options = options == null ? ImageExtractionOptions.defaults() : options;
    }

    @Override
    public String pluginId() {
        return "rawdata-image";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of(new ImageContentProcessor(new ImageSegmentComposer(), options));
    }

    @Override
    public List<RawDataIngestionPolicy> ingestionPolicies() {
        return List.of(
                new RawDataIngestionPolicy(
                        ContentTypes.IMAGE,
                        Set.of(ContentGovernanceType.IMAGE_CAPTION_OCR),
                        options.sourceLimit()));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new ImageRawContentTypeRegistrar());
    }
}
