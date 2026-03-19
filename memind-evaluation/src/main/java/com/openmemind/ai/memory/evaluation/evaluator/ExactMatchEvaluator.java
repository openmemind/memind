package com.openmemind.ai.memory.evaluation.evaluator;

import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.QuestionJudgment;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Exact match evaluator, used for multiple-choice scenarios, extracts option letters by priority using regex and compares
 *
 */
@Component
public class ExactMatchEvaluator implements AnswerEvaluator {

    /** Regex option extraction patterns, tried in order of priority from high to low */
    private static final Pattern[] OPTION_PATTERNS = {
        Pattern.compile("\\(([a-zA-Z])\\)"), // (a) — highest priority
        Pattern.compile("([a-zA-Z])\\)"), // a)
        Pattern.compile("([a-zA-Z])\\."), // a.
        Pattern.compile("^([a-zA-Z])$"), // single letter A — lowest priority
    };

    @Override
    public Mono<QuestionJudgment> evaluate(AnswerResult answer, QAPair qa) {
        String gen = extractOption(answer.generatedAnswer());
        String golden = extractOption(qa.goldenAnswer());
        boolean correct = gen.equalsIgnoreCase(golden);
        return Mono.just(QuestionJudgment.exact(answer, correct));
    }

    /**
     * Tries to extract option letters using regex in order of priority, if all fail, fallback to trim+toLowerCase
     *
     * @param s original answer string
     * @return extracted letter (lowercase), or the result of trim+toLowerCase
     */
    String extractOption(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        for (Pattern p : OPTION_PATTERNS) {
            Matcher m = p.matcher(trimmed);
            if (m.find()) return m.group(1).toLowerCase();
        }
        return trimmed.toLowerCase();
    }
}
