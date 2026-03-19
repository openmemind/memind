package com.openmemind.ai.memory.evaluation.evaluator;

import com.openmemind.ai.memory.evaluation.config.EvaluationProperties;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import com.openmemind.ai.memory.evaluation.pipeline.model.AnswerResult;
import com.openmemind.ai.memory.evaluation.pipeline.model.QuestionJudgment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * LLM Judge, through multiple independent calls to LLM to determine the correctness of answers, using majority vote to reach a final conclusion.
 * The prompt is consistent with EverMemOS evaluation/config/prompts.yaml llm_judge.
 *
 */
@Component
public class LlmJudgeEvaluator implements AnswerEvaluator {

    /** Consistent with prompts.yaml llm_judge.system_prompt */
    private static final String JUDGE_SYSTEM_PROMPT =
            "You are an expert grader that determines if answers to questions match a gold standard"
                    + " answer";

    /** Consistent with prompts.yaml llm_judge.user_prompt, placeholders {question}/{golden_answer}/{generated_answer} */
    private static final String JUDGE_USER_PROMPT =
            """
            Your task is to label an answer to a question as 'CORRECT' or 'WRONG'. You will be given the following data:
                (1) a question (posed by one user to another user),
                (2) a 'gold' (ground truth) answer,
                (3) a generated answer
            which you will score as CORRECT/WRONG.

            The point of the question is to ask about something one user should know about the other user based on their prior conversations.
            The gold answer will usually be a concise and short answer that includes the referenced topic, for example:
            Question: Do you remember what I got the last time I went to Hawaii?
            Gold answer: A shell necklace
            The generated answer might be much longer, but you should be generous with your grading - as long as it touches on the same topic as the gold answer, it should be counted as CORRECT.

            For time related questions, the gold answer will be a specific date, month, year, etc. The generated answer might be much longer or use relative time references (like "last Tuesday" or "next month"), but you should be generous with your grading - as long as it refers to the same date or time period as the gold answer, it should be counted as CORRECT. Even if the format differs (e.g., "May 7th" vs "7 May"), consider it CORRECT if it's the same date.

            Now it's time for the real question:
            Question: {question}
            Gold answer: {golden_answer}
            Generated answer: {generated_answer}

            First, provide a short (one sentence) explanation of your reasoning, then finish with CORRECT or WRONG.
            Do NOT include both CORRECT and WRONG in your response, or it will break the evaluation script.

            Just return the label CORRECT or WRONG in a json format with the key as "label".
            """;

    private static final Pattern LABEL_PATTERN =
            Pattern.compile("\"label\"\\s*:\\s*\"(CORRECT|WRONG)\"", Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;

    /** Independent evaluation count, read from EvaluationProperties.system.llm.numRuns */
    private final int numRuns;

    public LlmJudgeEvaluator(ChatClient chatClient, EvaluationProperties props) {
        this.chatClient = chatClient;
        this.numRuns = props.getSystem().getLlm().getNumRuns();
    }

    @Override
    public Mono<QuestionJudgment> evaluate(AnswerResult answer, QAPair qa) {
        String userPrompt =
                JUDGE_USER_PROMPT
                        .replace("{question}", qa.question())
                        .replace("{golden_answer}", qa.goldenAnswer())
                        .replace("{generated_answer}", answer.generatedAnswer());

        return Flux.range(0, numRuns)
                .flatMapSequential(
                        i -> callJudge(userPrompt).map(r -> Map.entry("judgment_" + (i + 1), r)))
                .collectList()
                .map(
                        entries -> {
                            Map<String, Boolean> judgments = new LinkedHashMap<>();
                            entries.forEach(e -> judgments.put(e.getKey(), e.getValue()));

                            // Calculate the score for each run
                            List<Double> runScores =
                                    judgments.values().stream().map(v -> v ? 1.0 : 0.0).toList();
                            double mean =
                                    runScores.stream().mapToDouble(d -> d).average().orElse(0.0);
                            double variance =
                                    runScores.stream()
                                            .mapToDouble(v -> (v - mean) * (v - mean))
                                            .average()
                                            .orElse(0.0);
                            double std = Math.sqrt(variance);

                            long trueCount = judgments.values().stream().filter(v -> v).count();
                            // majority vote: if more than half judge as CORRECT then correct
                            boolean correct = trueCount > numRuns / 2;

                            return QuestionJudgment.llm(
                                    answer, correct, judgments, runScores, mean, std);
                        });
    }

    private Mono<Boolean> callJudge(String userPrompt) {
        return Mono.fromCallable(
                        () -> {
                            String response =
                                    chatClient
                                            .prompt()
                                            .system(JUDGE_SYSTEM_PROMPT)
                                            .user(userPrompt)
                                            .options(
                                                    OpenAiChatOptions.builder()
                                                            .temperature(0.0)
                                                            .build())
                                            .call()
                                            .content();
                            return parseLabel(response);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Parse the JSON label returned by LLM, aligned with the parsing logic of EverMemOS llm_judge.py.
     * Prioritize matching {"label": "CORRECT"}, fallback to the text containing the keyword CORRECT.
     */
    private boolean parseLabel(String response) {
        if (response == null || response.isBlank()) return false;
        Matcher m = LABEL_PATTERN.matcher(response);
        if (m.find()) {
            return "CORRECT".equalsIgnoreCase(m.group(1));
        }
        // fallback: text contains CORRECT but does not contain WRONG
        String upper = response.toUpperCase();
        return upper.contains("CORRECT") && !upper.contains("WRONG");
    }
}
