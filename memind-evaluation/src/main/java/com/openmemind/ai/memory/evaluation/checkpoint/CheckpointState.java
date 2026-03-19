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
package com.openmemind.ai.memory.evaluation.checkpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Evaluation progress status, records the completed stages and the completion status of each conversation in the ADD stage, supports resuming from a breakpoint
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckpointState {
    private String datasetName;
    private String adapterName;
    private String runName;

    /** Set of completed stage names (ADD/SEARCH/ANSWER/EVALUATE) */
    private Set<String> completedStages = new LinkedHashSet<>();

    /** Completion status of each conversation in the ADD stage (for resuming within the ADD stage) */
    private Set<String> completedAddConvIds = new LinkedHashSet<>();

    private Instant lastUpdated;

    // Jackson needs no-arg constructor
    public CheckpointState() {}

    public CheckpointState(String datasetName, String adapterName, String runName) {
        this.datasetName = datasetName;
        this.adapterName = adapterName;
        this.runName = runName;
        this.lastUpdated = Instant.now();
    }

    public boolean isAddCompleted(String convId) {
        return completedAddConvIds.contains(convId);
    }

    public void markAddCompleted(String convId) {
        completedAddConvIds.add(convId);
        lastUpdated = Instant.now();
    }

    public boolean isStageCompleted(String stageName) {
        return completedStages.contains(stageName);
    }

    public void markStageCompleted(String stageName) {
        completedStages.add(stageName);
        lastUpdated = Instant.now();
    }

    // getters/setters for Jackson
    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String v) {
        this.datasetName = v;
    }

    public String getAdapterName() {
        return adapterName;
    }

    public void setAdapterName(String v) {
        this.adapterName = v;
    }

    public String getRunName() {
        return runName;
    }

    public void setRunName(String v) {
        this.runName = v;
    }

    public Set<String> getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(Set<String> v) {
        this.completedStages = v;
    }

    public Set<String> getCompletedAddConvIds() {
        return completedAddConvIds;
    }

    public void setCompletedAddConvIds(Set<String> v) {
        this.completedAddConvIds = v;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant v) {
        this.lastUpdated = v;
    }

    // convenience accessors
    public Set<String> completedAddConvIds() {
        return completedAddConvIds;
    }

    public Set<String> completedStages() {
        return completedStages;
    }
}
