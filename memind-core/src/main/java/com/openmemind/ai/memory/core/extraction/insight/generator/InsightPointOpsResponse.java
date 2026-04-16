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
package com.openmemind.ai.memory.core.extraction.insight.generator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.openmemind.ai.memory.core.data.PointOperation;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class InsightPointOpsResponse {

    private List<PointOperation> operations = List.of();

    @JsonIgnore private boolean explicitOperationsArray;

    public InsightPointOpsResponse() {}

    public InsightPointOpsResponse(List<PointOperation> operations) {
        setOperations(operations);
    }

    @JsonSetter("operations")
    public void setOperations(List<PointOperation> operations) {
        explicitOperationsArray = operations != null;
        this.operations = operations == null ? List.of() : List.copyOf(operations);
    }

    @JsonProperty("operations")
    public List<PointOperation> operations() {
        return operations;
    }

    @JsonIgnore
    public boolean hasExplicitOperationsArray() {
        return explicitOperationsArray;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InsightPointOpsResponse that)) {
            return false;
        }
        return explicitOperationsArray == that.explicitOperationsArray
                && Objects.equals(operations, that.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operations, explicitOperationsArray);
    }

    @Override
    public String toString() {
        return "InsightPointOpsResponse{"
                + "operations="
                + operations
                + ", explicitOperationsArray="
                + explicitOperationsArray
                + '}';
    }
}
