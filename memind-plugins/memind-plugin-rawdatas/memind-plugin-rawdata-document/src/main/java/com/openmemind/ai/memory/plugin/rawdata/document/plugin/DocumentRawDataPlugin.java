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

import com.openmemind.ai.memory.core.extraction.rawdata.RawContentProcessor;
import com.openmemind.ai.memory.core.extraction.rawdata.RawContentTypeRegistrar;
import com.openmemind.ai.memory.core.llm.ChatClientSlot;
import com.openmemind.ai.memory.core.plugin.RawDataIngestionPolicy;
import com.openmemind.ai.memory.core.plugin.RawDataPlugin;
import com.openmemind.ai.memory.core.plugin.RawDataPluginContext;
import com.openmemind.ai.memory.core.resource.ContentParser;
import com.openmemind.ai.memory.plugin.rawdata.document.DocumentSemantics;
import com.openmemind.ai.memory.plugin.rawdata.document.caption.LlmDocumentCaptionGenerator;
import com.openmemind.ai.memory.plugin.rawdata.document.chunk.ProfileAwareDocumentChunker;
import com.openmemind.ai.memory.plugin.rawdata.document.config.DocumentExtractionOptions;
import com.openmemind.ai.memory.plugin.rawdata.document.content.DocumentContent;
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
        var captionClient = context.chatClientRegistry().resolve(ChatClientSlot.CAPTION_GENERATOR);
        var captionGenerator =
                new LlmDocumentCaptionGenerator(
                        captionClient,
                        options.llmCaptionEnabled(),
                        options.captionConcurrency(),
                        options.fallbackCaptionMaxLength());
        return List.of(
                new DocumentContentProcessor(
                        new ProfileAwareDocumentChunker(), options, captionGenerator));
    }

    @Override
    public List<ContentParser> parsers(RawDataPluginContext context) {
        return List.of(new NativeTextDocumentContentParser(), new TikaDocumentContentParser());
    }

    @Override
    public List<RawDataIngestionPolicy> ingestionPolicies() {
        return List.of(
                new RawDataIngestionPolicy(
                        DocumentContent.TYPE,
                        DocumentSemantics.GOVERNANCE_TEXT_LIKE,
                        options.textLikeSourceLimit()),
                new RawDataIngestionPolicy(
                        DocumentContent.TYPE,
                        DocumentSemantics.GOVERNANCE_BINARY,
                        options.binarySourceLimit()));
    }

    @Override
    public List<RawContentTypeRegistrar> typeRegistrars() {
        return List.of(new DocumentRawContentTypeRegistrar());
    }
}
