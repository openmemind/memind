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
package com.openmemind.ai.memory.evaluation.dataset.converter;

import java.nio.file.Path;

/**
 * Dataset format converter interface, converts non-LoCoMo format to LoCoMo JSON
 *
 */
public interface DatasetConverter {

    /** Returns the source format identifier supported by this converter, such as "longmemeval", "personamem" */
    String sourceFormat();

    /**
     * Converts the file pointed to by inputPath to LoCoMo JSON and outputs to the outputDir directory
     *
     * @return The full path of the converted file
     */
    Path convert(Path inputPath, Path outputDir);
}
