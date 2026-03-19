package com.openmemind.ai.memory.evaluation.dataset.converter;

import static java.util.stream.Collectors.toMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dataset converter registry, automatically scans all DatasetConverter implementations
 *
 */
@Component
public class ConverterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConverterRegistry.class);

    /** Index all converters by sourceFormat */
    private final Map<String, DatasetConverter> converters;

    public ConverterRegistry(List<DatasetConverter> converterList) {
        this.converters =
                converterList.stream()
                        .collect(toMap(DatasetConverter::sourceFormat, Function.identity()));
        log.info("Registered dataset converters: {}", converters.keySet());
    }

    /**
     * If there is a corresponding converter for datasetName, convert it; skip if the output file already exists; otherwise return the original path
     *
     * @param datasetName Dataset name (e.g. "locomo", "longmemeval")
     * @param inputPath   Original data file path
     * @param outputDir   Conversion output directory
     * @return The final file path used for loading
     */
    public Path convertIfNeeded(String datasetName, Path inputPath, Path outputDir) {
        DatasetConverter converter = converters.get(datasetName);
        if (converter == null) {
            // No corresponding converter (e.g. locomo), return the original path directly
            return inputPath;
        }
        // Output file name: {format}_converted.json
        Path outputFile = outputDir.resolve(datasetName + "_converted.json");
        if (Files.exists(outputFile)) {
            log.info("Conversion output already exists, skipping conversion: {}", outputFile);
            return outputFile;
        }
        log.info("Converting dataset {} -> {}", inputPath, outputFile);
        return converter.convert(inputPath, outputDir);
    }
}
