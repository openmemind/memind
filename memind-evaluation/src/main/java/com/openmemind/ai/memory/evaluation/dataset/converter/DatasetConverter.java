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
