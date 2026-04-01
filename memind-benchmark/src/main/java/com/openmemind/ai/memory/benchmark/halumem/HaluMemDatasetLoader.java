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
package com.openmemind.ai.memory.benchmark.halumem;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class HaluMemDatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public HaluMemDataset load(Path path) {
        try {
            List<HaluMemDataset.HaluMemUser> users =
                    Files.readAllLines(path).stream()
                            .filter(line -> !line.isBlank())
                            .map(this::parseUser)
                            .toList();
            return new HaluMemDataset("halumem", List.copyOf(users));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load HaluMem dataset from " + path, exception);
        }
    }

    private HaluMemDataset.HaluMemUser parseUser(String rawLine) {
        try {
            return objectMapper.readValue(rawLine, HaluMemDataset.HaluMemUser.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse HaluMem user line", exception);
        }
    }
}
