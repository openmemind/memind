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
package com.openmemind.ai.memory.plugin.rawdata.agent.chunk;

import com.openmemind.ai.memory.core.extraction.rawdata.segment.CharBoundary;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.Segment;
import com.openmemind.ai.memory.core.extraction.rawdata.segment.SegmentRuntimeContext;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentChunkingOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.config.AgentPrivacyOptions;
import com.openmemind.ai.memory.plugin.rawdata.agent.content.AgentTimelineContent;
import com.openmemind.ai.memory.plugin.rawdata.agent.model.AgentEpisode;
import com.openmemind.ai.memory.plugin.rawdata.agent.privacy.AgentEventRedactor;
import java.util.List;

/**
 * Redacts, assembles, and formats agent timeline content into rawdata segments.
 */
public final class AgentTimelineChunker {

    private final AgentEpisodeAssembler assembler;
    private final AgentSegmentFormatter formatter;
    private final AgentEventRedactor redactor;

    public AgentTimelineChunker() {
        this(AgentChunkingOptions.defaults(), new AgentPrivacyOptions());
    }

    public AgentTimelineChunker(
            AgentChunkingOptions chunkingOptions, AgentPrivacyOptions privacyOptions) {
        this(
                new AgentEpisodeAssembler(chunkingOptions),
                new AgentSegmentFormatter(),
                new AgentEventRedactor(privacyOptions));
    }

    AgentTimelineChunker(
            AgentEpisodeAssembler assembler,
            AgentSegmentFormatter formatter,
            AgentEventRedactor redactor) {
        this.assembler = assembler;
        this.formatter = formatter;
        this.redactor = redactor;
    }

    public List<Segment> chunk(AgentTimelineContent content) {
        if (content == null || content.events().isEmpty()) {
            return List.of();
        }
        AgentTimelineContent redactedContent =
                new AgentTimelineContent(
                        content.sourceClient(),
                        content.sourceVersion(),
                        content.sessionId(),
                        content.agentTurnId(),
                        content.timelineId(),
                        content.project(),
                        content.events().stream().map(redactor::redact).toList(),
                        content.metadata());
        return assembler.assemble(redactedContent).stream()
                .map(episode -> toSegment(redactedContent, episode))
                .toList();
    }

    private Segment toSegment(AgentTimelineContent content, AgentEpisode episode) {
        AgentSegmentFormatter.FormattedSegment formatted = formatter.format(content, episode);
        return new Segment(
                formatted.content(),
                null,
                new CharBoundary(0, formatted.content().length()),
                formatted.metadata(),
                new SegmentRuntimeContext(
                        episode.startTime(), episode.endTime(), null, content.sourceClient()));
    }
}
