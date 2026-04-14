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
package com.openmemind.ai.memory.plugin.rawdata.document.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

final class CsvTextShaper {

    private static final Set<String> COMMON_HEADER_KEYS =
            Set.of(
                    "id",
                    "name",
                    "title",
                    "team",
                    "role",
                    "email",
                    "phone",
                    "company",
                    "department",
                    "status",
                    "type",
                    "category",
                    "owner",
                    "created_at",
                    "updated_at",
                    "timestamp",
                    "date");

    String shape(String csv) {
        return shape(csv, detectHeader(csv));
    }

    String shape(String csv, boolean firstRowIsHeader) {
        if (csv == null || csv.isBlank()) {
            return "";
        }
        CSVFormat format =
                firstRowIsHeader
                        ? CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                        : CSVFormat.DEFAULT;
        try (CSVParser parser = format.parse(new StringReader(csv))) {
            List<String> rows = new ArrayList<>();
            int rowNumber = 1;
            for (CSVRecord record : parser) {
                if (record.stream().allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                rows.add("Row " + rowNumber++ + ":");
                rows.add(renderRow(parser, record, firstRowIsHeader));
                rows.add("");
            }
            if (!rows.isEmpty() && rows.getLast().isEmpty()) {
                rows.removeLast();
            }
            return String.join("\n", rows);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV text", e);
        }
    }

    private boolean detectHeader(String csv) {
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(csv))) {
            List<CSVRecord> rows =
                    parser.stream()
                            .filter(
                                    record ->
                                            record.stream()
                                                    .anyMatch(
                                                            value ->
                                                                    value != null
                                                                            && !value.isBlank()))
                            .limit(2)
                            .toList();
            if (rows.size() < 2) {
                return false;
            }
            CSVRecord header = rows.getFirst();
            CSVRecord sample = rows.get(1);
            if (header.size() != sample.size()) {
                return false;
            }
            int score = 0;
            for (int index = 0; index < header.size(); index++) {
                String headerValue = header.get(index).trim();
                String sampleValue = sample.get(index).trim();
                if (looksLikeHeaderLabel(headerValue)) {
                    score++;
                }
                if (COMMON_HEADER_KEYS.contains(normalizeKey(headerValue))) {
                    score += 2;
                }
                if (looksLikeTypedDataValue(sampleValue)) {
                    score++;
                }
                if (!normalizeKey(headerValue).equals(normalizeKey(sampleValue))) {
                    score++;
                }
            }
            return score >= (header.size() * 2) + 1;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to inspect CSV header", e);
        }
    }

    private String renderRow(CSVParser parser, CSVRecord record, boolean firstRowIsHeader) {
        List<String> cells = new ArrayList<>();
        if (firstRowIsHeader) {
            for (String name : parser.getHeaderNames()) {
                cells.add(name + ": " + record.get(name).trim());
            }
            return String.join(", ", cells);
        }
        IntStream.range(0, record.size())
                .forEach(
                        index ->
                                cells.add(
                                        "column" + (index + 1) + ": " + record.get(index).trim()));
        return String.join(", ", cells);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeHeaderLabel(String value) {
        String trimmed = value == null ? "" : value.trim();
        return !trimmed.isBlank() && trimmed.matches("[a-z][a-z0-9_\\- ]*");
    }

    private boolean looksLikeTypedDataValue(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.matches(".*\\d.*")
                || normalized.contains("@")
                || normalized.contains("://");
    }
}
