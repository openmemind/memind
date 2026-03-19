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
package com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = URLSource.class, name = "url"),
    @JsonSubTypes.Type(value = Base64Source.class, name = "base64")
})
public abstract sealed class Source permits URLSource, Base64Source {

    /**
     * Returns the identifier string of the source
     *
     * <ul>
     *   <li>URLSource: returns URL
     *   <li>Base64Source: returns data URI (data:mediaType;base64,...)
     * </ul>
     */
    public abstract String toIdentifier();

    /**
     * Returns the content identifier of the source
     *
     * <ul>
     *   <li>URLSource: directly returns URL
     *   <li>Base64Source: samples SHA-256 of data, returns 16-bit hex
     * </ul>
     */
    public abstract String getContentId();
}
