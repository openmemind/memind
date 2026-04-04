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
package com.openmemind.ai.memory.evaluation.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves the active dataset profile from evaluation properties.
 */
@Component
public class ActiveDatasetProfileResolver {

    private final EvaluationProperties props;

    public ActiveDatasetProfileResolver(EvaluationProperties props) {
        this.props = props;
    }

    public DatasetProfile resolve() {
        String activeDataset = props.getActiveDataset();
        Map<String, EvaluationProperties.DatasetProperties> datasets = props.getDatasets();

        if (datasets != null && !datasets.isEmpty()) {
            EvaluationProperties.DatasetProperties dataset = datasets.get(activeDataset);
            if (dataset == null) {
                throw new IllegalArgumentException("Unknown active dataset: " + activeDataset);
            }
            return toProfile(activeDataset, dataset);
        }

        EvaluationProperties.DatasetProperties legacyDataset = props.getDataset();
        if (legacyDataset != null && activeDataset.equals(legacyDataset.getName())) {
            return toProfile(legacyDataset.getName(), legacyDataset);
        }

        throw new IllegalArgumentException("Unknown active dataset: " + activeDataset);
    }

    private DatasetProfile toProfile(
            String datasetName, EvaluationProperties.DatasetProperties dataset) {
        Path path =
                dataset.getPath() == null || dataset.getPath().isBlank()
                        ? null
                        : Path.of(dataset.getPath());
        List<String> filterCategories =
                dataset.getFilterCategories() == null
                        ? List.of()
                        : List.copyOf(dataset.getFilterCategories());
        return new DatasetProfile(
                datasetName,
                path,
                dataset.getSourceFormat(),
                dataset.getLoaderFormat(),
                dataset.getMaxContentLength(),
                filterCategories,
                dataset.getSearch().getQueryMode(),
                dataset.getJudge().getStrategy());
    }
}
