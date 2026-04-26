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
package com.openmemind.ai.memory.core.data.enums;

/**
 * Normalized thread event types.
 */
public enum MemoryThreadEventType {
    OBSERVATION,
    UPDATE,
    STATE_CHANGE,
    BLOCKER_ADDED,
    BLOCKER_CLEARED,
    DECISION_MADE,
    QUESTION_OPENED,
    QUESTION_CLOSED,
    MILESTONE_REACHED,
    SETBACK,
    RESOLUTION_DECLARED
}
