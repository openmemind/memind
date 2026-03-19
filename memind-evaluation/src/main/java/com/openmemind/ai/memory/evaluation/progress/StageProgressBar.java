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
package com.openmemind.ai.memory.evaluation.progress;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

/**
 * Encapsulates me.tongfei:progressbar, providing a tqdm style progress bar for each pipeline stage.
 *
 * <p>Supports checkpoint recovery: set the initial completed steps through the {@code initial} parameter.
 *
 */
public class StageProgressBar implements AutoCloseable {

    private final ProgressBar pb;

    private StageProgressBar(ProgressBar pb) {
        this.pb = pb;
    }

    /**
     * Create and start the progress bar.
     *
     * @param name    stage name, displayed on the left side of the progress bar
     * @param total   total steps
     * @param initial initial completed steps (used for checkpoint recovery)
     */
    public static StageProgressBar create(String name, long total, long initial) {
        ProgressBar pb =
                new ProgressBarBuilder()
                        .setTaskName(name)
                        .setInitialMax(total)
                        .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
                        .build();
        if (initial > 0) {
            pb.stepBy(initial);
        }
        return new StageProgressBar(pb);
    }

    /** Step forward. */
    public void step() {
        pb.step();
    }

    /** Step forward n steps. */
    public void stepBy(long n) {
        pb.stepBy(n);
    }

    /** Close the progress bar and release resources. */
    @Override
    public void close() {
        pb.close();
    }
}
