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
package com.openmemind.ai.memory.evaluation.dataset;

import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import java.nio.file.Path;

/**
 * Dataset loader interface, parses external data files into a unified EvalDataset model
 *
 */
public interface DatasetLoader {
    String datasetName();

    EvalDataset load(Path dataPath);
}
