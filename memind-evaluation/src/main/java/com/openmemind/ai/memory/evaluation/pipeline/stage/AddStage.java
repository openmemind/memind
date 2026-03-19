package com.openmemind.ai.memory.evaluation.pipeline.stage;

import com.openmemind.ai.memory.evaluation.adapter.BaseMemoryAdapter;
import com.openmemind.ai.memory.evaluation.adapter.MemoryAdapter;
import com.openmemind.ai.memory.evaluation.adapter.model.AddRequest;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointState;
import com.openmemind.ai.memory.evaluation.checkpoint.CheckpointStore;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalConversation;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalDataset;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalMessage;
import com.openmemind.ai.memory.evaluation.pipeline.PipelineConfig;
import com.openmemind.ai.memory.evaluation.progress.StageProgressBar;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ADD Stage: Write conversation messages to the memory system, supporting cleanGroups, smoke, and conversation-level checkpoint continuation
 *
 */
@Component
public class AddStage {
    private static final Logger log = LoggerFactory.getLogger(AddStage.class);
    private final CheckpointStore checkpointStore;

    public AddStage(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
    }

    public Mono<Void> run(
            EvalDataset dataset,
            MemoryAdapter adapter,
            CheckpointState checkpoint,
            PipelineConfig config) {
        List<EvalConversation> conversations = applyRange(dataset.conversations(), config);
        List<EvalConversation> pending =
                conversations.stream()
                        .filter(conv -> !checkpoint.isAddCompleted(conv.conversationId()))
                        .toList();
        // Per-message progress (matching Python: advance by message count per conversation)
        long totalMessages =
                conversations.stream()
                        .mapToLong(conv -> applySmoke(conv.messages(), config).size())
                        .sum();
        long doneMessages =
                conversations.stream()
                        .filter(conv -> checkpoint.isAddCompleted(conv.conversationId()))
                        .mapToLong(conv -> applySmoke(conv.messages(), config).size())
                        .sum();
        StageProgressBar pb = StageProgressBar.create("Add    ", totalMessages, doneMessages);

        return Flux.fromIterable(pending)
                .flatMap(
                        conv -> {
                            String speakerAId =
                                    BaseMemoryAdapter.buildUserId(
                                            conv.conversationId(), conv.speakerA());
                            String speakerBId =
                                    BaseMemoryAdapter.buildUserId(
                                            conv.conversationId(), conv.speakerB());
                            List<EvalMessage> messages = applySmoke(conv.messages(), config);
                            log.info(
                                    "[Add] Processing conv={} ({} messages)",
                                    conv.conversationId(),
                                    messages.size());

                            AddRequest req =
                                    new AddRequest(
                                            conv.conversationId(),
                                            speakerAId,
                                            speakerBId,
                                            messages);
                            // If cleanGroups is enabled, clear the existing memory of the
                            // conversation before adding
                            Mono<Void> cleanMono =
                                    config.cleanGroups()
                                            ? adapter.clean(conv.conversationId())
                                            : Mono.empty();
                            return cleanMono
                                    .then(adapter.add(req))
                                    .retryWhen(
                                            reactor.util.retry.Retry.backoff(
                                                            3, java.time.Duration.ofSeconds(2))
                                                    .maxBackoff(java.time.Duration.ofSeconds(15)))
                                    .doOnSuccess(
                                            r -> {
                                                log.info(
                                                        "[Add] conv={} new={} reinforced={}"
                                                                + " insights={}",
                                                        conv.conversationId(),
                                                        r.newItemCount(),
                                                        r.reinforcedItemCount(),
                                                        r.insightCount());
                                                checkpoint.markAddCompleted(conv.conversationId());
                                                checkpointStore.save(checkpoint);
                                                pb.stepBy(messages.size());
                                            })
                                    .doOnError(
                                            e ->
                                                    log.warn(
                                                            "[Add] conv={} failed: {}",
                                                            conv.conversationId(),
                                                            e.getMessage()))
                                    .onErrorResume(e -> Mono.empty());
                        },
                        config.addConcurrency())
                .then()
                .doFinally(signal -> pb.close());
    }

    // Extract a subset of conversations based on fromConv/toConv
    private List<EvalConversation> applyRange(List<EvalConversation> convs, PipelineConfig config) {
        int from = config.fromConv();
        int to = config.toConv() == -1 ? convs.size() : Math.min(config.toConv(), convs.size());
        return convs.subList(from, to);
    }

    // In smoke mode, only take the first N messages
    private List<EvalMessage> applySmoke(List<EvalMessage> messages, PipelineConfig config) {
        if (!config.smoke()) return messages;
        return messages.subList(0, Math.min(config.smokeMessages(), messages.size()));
    }
}
