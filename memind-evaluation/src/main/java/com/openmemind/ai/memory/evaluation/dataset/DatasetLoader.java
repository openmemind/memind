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
