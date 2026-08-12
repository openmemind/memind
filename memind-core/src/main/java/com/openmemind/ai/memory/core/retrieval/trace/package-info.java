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
/**
 * Request-scoped retrieval debug trace support.
 *
 * <p>The retrieval trace is a user-facing debug projection built from Micrometer Observation
 * callbacks. It is intentionally separate from the normal meter/span exporters: every retrieval
 * request that asks for tracing owns a {@link RetrievalTraceRecorder}, and that recorder is passed
 * through Reactor Context instead of being stored in the shared ObservationRegistry.
 *
 * <p>The flow is:
 *
 * <ol>
 *   <li>The server creates a bounded recorder when the request sets {@code trace=true}.
 *   <li>{@link RetrievalTraceContext} puts that recorder into the reactive chain.
 *   <li>Memory observation contexts copy the recorder from Reactor Context.
 *   <li>{@link RetrievalTraceObservationHandler} runs when each Observation stops.
 *   <li>Observation contexts that implement {@link RetrievalTraceEventSource} convert their
 *       component-specific result into a normalized {@link RetrievalTraceEvent}.
 *   <li>The recorder aggregates those events into a {@link RetrievalDebugTrace} snapshot for the
 *       response body.
 * </ol>
 *
 * <p>The objects in this package should stay bounded and serializable. They are for debugging a
 * single retrieval call, not for durable telemetry storage.
 */
package com.openmemind.ai.memory.core.retrieval.trace;
