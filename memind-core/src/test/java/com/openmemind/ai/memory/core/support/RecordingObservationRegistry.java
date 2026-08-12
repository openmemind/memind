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
package com.openmemind.ai.memory.core.support;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingObservationRegistry implements ObservationRegistry {

    private final ObservationRegistry delegate = ObservationRegistry.create();
    private final Map<Observation.Context, Map<String, Object>> started = new IdentityHashMap<>();
    private final List<RecordedObservation> observations = new CopyOnWriteArrayList<>();

    public RecordingObservationRegistry() {
        delegate.observationConfig().observationHandler(new RecordingObservationHandler());
    }

    @Override
    public Observation getCurrentObservation() {
        return delegate.getCurrentObservation();
    }

    @Override
    public Observation.Scope getCurrentObservationScope() {
        return delegate.getCurrentObservationScope();
    }

    @Override
    public void setCurrentObservationScope(Observation.Scope current) {
        delegate.setCurrentObservationScope(current);
    }

    @Override
    public ObservationRegistry.ObservationConfig observationConfig() {
        return delegate.observationConfig();
    }

    public List<RecordedObservation> observations() {
        return List.copyOf(observations);
    }

    public List<RecordedObservation> monoContexts() {
        return observations();
    }

    private static Map<String, Object> toMap(KeyValues keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        keyValues.forEach(keyValue -> values.put(keyValue.getKey(), keyValue.getValue()));
        return Map.copyOf(values);
    }

    private final class RecordingObservationHandler
            implements ObservationHandler<Observation.Context> {

        @Override
        public void onStart(Observation.Context context) {
            synchronized (started) {
                started.put(context, toMap(context.getAllKeyValues()));
            }
        }

        @Override
        public void onStop(Observation.Context context) {
            Map<String, Object> requestAttributes;
            synchronized (started) {
                requestAttributes = started.remove(context);
            }
            Map<String, Object> finalAttributes = toMap(context.getAllKeyValues());
            observations.add(
                    new RecordedObservation(
                            context.getName(),
                            requestAttributes == null ? Map.of() : requestAttributes,
                            finalAttributes));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
