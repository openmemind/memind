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
package com.openmemind.ai.memory.core.extraction.source;

import java.util.Arrays;
import java.util.Objects;

/**
 * Extraction source backed by uploaded file bytes.
 */
public final class FileExtractionSource implements ExtractionSource {

    private final String fileName;
    private final byte[] data;
    private final String mimeType;

    public FileExtractionSource(String fileName, byte[] data, String mimeType) {
        this.fileName = Objects.requireNonNull(fileName, "fileName is required");
        this.data = Arrays.copyOf(Objects.requireNonNull(data, "data is required"), data.length);
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType is required");
    }

    public String fileName() {
        return fileName;
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileExtractionSource that)) {
            return false;
        }
        return Objects.equals(fileName, that.fileName)
                && Arrays.equals(data, that.data)
                && Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(fileName, mimeType);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "FileExtractionSource[fileName=%s, mimeType=%s, sizeBytes=%d]"
                .formatted(fileName, mimeType, data.length);
    }
}
