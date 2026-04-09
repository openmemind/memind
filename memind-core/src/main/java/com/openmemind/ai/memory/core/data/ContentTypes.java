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
package com.openmemind.ai.memory.core.data;

/**
 * Well-known content type identifiers.
 * UPPERCASE for backward compatibility with existing database data.
 */
public final class ContentTypes {

    public static final String CONVERSATION = "CONVERSATION";
    public static final String TOOL_CALL = "TOOL_CALL";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String IMAGE = "IMAGE";
    public static final String AUDIO = "AUDIO";

    private ContentTypes() {}
}
