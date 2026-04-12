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
package com.openmemind.ai.memory.plugin.rawdata.document.plugin;

import com.openmemind.ai.memory.core.data.ContentTypes;
import com.openmemind.ai.memory.core.data.enums.ContentGovernanceType;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.chunk.ProfileAwareDocumentChunker;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.document.parser.NativeTextDocumentContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.parser.tika.TikaDocumentContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.processor.DocumentContentProcessor;
import java.util.List;

public final class DocumentRawDataPlugin implements RawDataPlugin {

    private final DocumentExtractionOptions options;

    public DocumentRawDataPlugin() {
        this(DocumentExtractionOptions.defaults());
    }

    public DocumentRawDataPlugin(DocumentExtractionOptions options) {
        this.options = options == null ? DocumentExtractionOptions.defaults() : options;
    }

    @Override
    public String pluginId() {
        return "rawdata-document";
    }

    @Override
    public List<RawContentProcessor<?>> processors(RawDataPluginContext context) {
        return List.of(new DocumentContentProcessor(new ProfileAwareDocumentChunker(), options));
    }

    @Override
    public List<ContentParser> parsers(RawDataPluginContext context) {
        return List.of(new NativeTextDocumentContentParser(), new TikaDocumentContentParser());
    }

    @Override
    public List<RawDataIngestionPolicy> ingestionPolicies() {
        return List.of(
                new RawDataIngestionPolicy(
                        ContentTypes.DOCUMENT,
                        java.util.Set.of(ContentGovernanceType.DOCUMENT_TEXT_LIKE),
                        options.textLikeSourceLimit()),
                new RawDataIngestionPolicy(
                        ContentTypes.DOCUMENT,
                        java.util.Set.of(ContentGovernanceType.DOCUMENT_BINARY),
                        options.binarySourceLimit()));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new DocumentRawContentTypeRegistrar());
    }
}
