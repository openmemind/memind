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
package com.openmemind.ai.memory.example.java.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the shared example data directory for all example modules.
 */
public final class SharedExampleDataLocator {

    private static final String DATA_DIR_PROPERTY = "memind.examples.data-dir";
    private static final String DATA_DIR_ENV = "MEMIND_EXAMPLES_DATA_DIR";

    public Path resolveExampleDataRoot() {
        String configured = System.getProperty(DATA_DIR_PROPERTY);
        if (hasText(configured)) {
            return validateDirectory(Path.of(configured));
        }

        String configuredEnv = System.getenv(DATA_DIR_ENV);
        if (hasText(configuredEnv)) {
            return validateDirectory(Path.of(configuredEnv));
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        Path detected = detectFromWorkspace(current);
        if (detected != null) {
            return detected;
        }

        throw new IllegalStateException(
                "Failed to locate shared example data directory. "
                        + "Set system property '"
                        + DATA_DIR_PROPERTY
                        + "' or environment variable '"
                        + DATA_DIR_ENV
                        + "'.");
    }

    private Path detectFromWorkspace(Path start) {
        for (Path cursor = start; cursor != null; cursor = cursor.getParent()) {
            Path localCandidate = resolveLocalCandidate(cursor);
            if (Files.isDirectory(localCandidate)) {
                return localCandidate.toAbsolutePath().normalize();
            }

            Path nestedCandidate = cursor.resolve("memind-examples").resolve("data");
            if (Files.isDirectory(nestedCandidate)) {
                return nestedCandidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private Path resolveLocalCandidate(Path cursor) {
        if (cursor.getFileName() != null
                && "memind-examples".equals(cursor.getFileName().toString())) {
            return cursor.resolve("data");
        }
        return cursor.resolve("data");
    }

    private Path validateDirectory(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalStateException(
                    "Configured shared example data directory does not exist: " + normalized);
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
