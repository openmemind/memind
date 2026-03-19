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
package com.openmemind.ai.memory.core.extraction.streaming;

import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Boundary Detector
 *
 * <p>Determines whether the current message buffer should be sealed, triggering memory extraction.
 *
 */
public interface BoundaryDetector {

    /**
     * Determines whether the current buffer should be sealed
     *
     * @param buffer Current message buffer
     * @param context Detection context (including the summary of the previous topic, time interval, etc.)
     * @return Boundary detection decision
     */
    Mono<BoundaryDecision> shouldSeal(List<Message> buffer, BoundaryDetectionContext context);
}
