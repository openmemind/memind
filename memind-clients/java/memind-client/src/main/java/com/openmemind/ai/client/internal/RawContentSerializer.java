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
package com.openmemind.ai.client.internal;

import com.openmemind.ai.client.model.common.ConversationContent;
import com.openmemind.ai.client.model.common.MapRawContent;
import com.openmemind.ai.client.model.common.RawContent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class RawContentSerializer extends ValueSerializer<RawContent> {

    @Override
    public void serialize(RawContent value, JsonGenerator gen, SerializationContext context)
            throws JacksonException {
        gen.writeStartObject();
        gen.writeStringProperty("type", value.type());

        if (value instanceof ConversationContent conv) {
            context.defaultSerializeProperty("messages", conv.getMessages(), gen);
        } else if (value instanceof MapRawContent map) {
            for (var entry : map.getProperties().entrySet()) {
                context.defaultSerializeProperty(entry.getKey(), entry.getValue(), gen);
            }
        }

        gen.writeEndObject();
    }
}
