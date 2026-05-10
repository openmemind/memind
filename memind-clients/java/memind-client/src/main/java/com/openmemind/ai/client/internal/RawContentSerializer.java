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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.openmemind.ai.client.model.common.ConversationContent;
import com.openmemind.ai.client.model.common.MapRawContent;
import com.openmemind.ai.client.model.common.RawContent;
import java.io.IOException;

public class RawContentSerializer extends JsonSerializer<RawContent> {

    @Override
    public void serialize(RawContent value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", value.type());

        if (value instanceof ConversationContent conv) {
            gen.writeObjectField("messages", conv.getMessages());
        } else if (value instanceof MapRawContent map) {
            for (var entry : map.getProperties().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
        }

        gen.writeEndObject();
    }
}
