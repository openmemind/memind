package com.openmemind.ai.memory.evaluation.adapter.memind;

import com.openmemind.ai.memory.core.data.DefaultMemoryId;
import com.openmemind.ai.memory.core.data.MemoryId;
import com.openmemind.ai.memory.core.extraction.ExtractionConfig;
import com.openmemind.ai.memory.core.extraction.ExtractionRequest;
import com.openmemind.ai.memory.core.extraction.ExtractionResult;
import com.openmemind.ai.memory.core.extraction.MemoryExtractionPipeline;
import com.openmemind.ai.memory.core.extraction.rawdata.content.ConversationContent;
import com.openmemind.ai.memory.core.extraction.rawdata.content.conversation.message.Message;
import com.openmemind.ai.memory.core.extraction.streaming.BoundaryDetector;
import com.openmemind.ai.memory.core.extraction.streaming.ConversationBufferStore;
import com.openmemind.ai.memory.core.retrieval.MemoryRetriever;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalConfig.TierConfig;
import com.openmemind.ai.memory.core.retrieval.RetrievalRequest;
import com.openmemind.ai.memory.core.retrieval.RetrievalResult;
import com.openmemind.ai.memory.core.retrieval.rerank.Reranker;
import com.openmemind.ai.memory.core.retrieval.scoring.ScoredResult;
import com.openmemind.ai.memory.core.store.MemoryStore;
import com.openmemind.ai.memory.evaluation.adapter.AddMode;
import com.openmemind.ai.memory.evaluation.adapter.BaseMemoryAdapter;
import com.openmemind.ai.memory.evaluation.adapter.model.AddRequest;
import com.openmemind.ai.memory.evaluation.adapter.model.AddResult;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchRequest;
import com.openmemind.ai.memory.evaluation.adapter.model.SearchResult;
import com.openmemind.ai.memory.evaluation.config.EvaluationProperties;
import com.openmemind.ai.memory.evaluation.dataset.model.EvalMessage;
import com.openmemind.ai.memory.evaluation.dataset.model.QAPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Memory adapter based on memind-core, supporting single perspective/double perspective extraction and retrieval, using 7-step CoT to answer questions
 *
 */
@Component
public class MemindAdapter extends BaseMemoryAdapter {

    private static final String AGENT_ID = "eval-agent";

    private static final String ANSWER_PROMPT =
            """
            You are an intelligent memory assistant tasked with retrieving accurate information from episodic memories.

            # CONTEXT:
            You have access to episodic memories from conversations between two speakers. These memories contain
            timestamped information that may be relevant to answering the question.

            # INSTRUCTIONS:
            Your goal is to synthesize information from all relevant memories to provide a comprehensive and accurate answer.
            You MUST follow a structured Chain-of-Thought process to ensure no details are missed.
            Actively look for connections between people, places, and events to build a complete picture. Synthesize information from different memories to answer the user's question.
            It is CRITICAL that you move beyond simple fact extraction and perform logical inference. When the evidence strongly suggests a connection, you must state that connection. Do not dismiss reasonable inferences as "speculation." Your task is to provide the most complete answer supported by the available evidence.

            # CRITICAL REQUIREMENTS:
            1. NEVER omit specific names - use "Amy's colleague Rob" not "a colleague"
            2. ALWAYS include exact numbers, amounts, prices, percentages, dates, times
            3. PRESERVE frequencies exactly - "every Tuesday and Thursday" not "twice a week"
            4. MAINTAIN all proper nouns and entities as they appear

            # RESPONSE FORMAT (You MUST follow this structure):

            ## STEP 1: RELEVANT MEMORIES EXTRACTION
            [List each memory that relates to the question, with its timestamp]
            - Memory 1: [timestamp] - [content]
            - Memory 2: [timestamp] - [content]
            ...

            ## STEP 2: KEY INFORMATION IDENTIFICATION
            [Extract ALL specific details from the memories]
            - Names mentioned: [list all person names, place names, company names]
            - Numbers/Quantities: [list all amounts, prices, percentages]
            - Dates/Times: [list all temporal information]
            - Frequencies: [list any recurring patterns]
            - Other entities: [list brands, products, etc.]

            ## STEP 3: CROSS-MEMORY LINKING
            [Identify entities that appear in multiple memories and link related information. Make reasonable inferences when entities are strongly connected.]
            - Shared entities: [list people, places, events mentioned across different memories]
            - Connections found: [e.g., "Memory 1 mentions A moved from hometown → Memory 2 mentions A's hometown is LA → Therefore A moved from LA"]
            - Inferred facts: [list any facts that require combining information from multiple memories]

            ## STEP 4: TIME REFERENCE CALCULATION
            [If applicable, convert relative time references]
            - Original reference: [e.g., "last year" from May 2022]
            - Calculated actual time: [e.g., "2021"]

            ## STEP 5: CONTRADICTION CHECK
            [If multiple memories contain different information]
            - Conflicting information: [describe]
            - Resolution: [explain which is most recent/reliable]

            ## STEP 6: DETAIL VERIFICATION CHECKLIST
            - [ ] All person names included: [list them]
            - [ ] All locations included: [list them]
            - [ ] All numbers exact: [list them]
            - [ ] All frequencies specific: [list them]
            - [ ] All dates/times precise: [list them]
            - [ ] All proper nouns preserved: [list them]

            ## STEP 7: ANSWER FORMULATION
            [Explain how you're combining the information to answer the question]

            ## FINAL ANSWER:
            [Provide the concise answer with ALL specific details preserved]

            ---

            {context}

            Question: {question}

            Now, follow the Chain-of-Thought process above to answer the question:
            """;

    private final MemoryExtractionPipeline extractor;
    private final MemoryRetriever retriever;
    private final ChatClient chatClient;
    private final BoundaryDetector boundaryDetector;
    private final ConversationBufferStore bufferStore;
    private final MemoryStore memoryStore;
    private final Reranker reranker;

    /** Is dual perspective: false=only use speakerA (default), true=speakerA+speakerB */
    private boolean dualPerspective;

    /** Memory extraction mode: STREAMING (default) or CHUNK */
    private AddMode addMode = AddMode.STREAMING;

    /** Extraction configuration: disable Insight, use PARALLEL mode */
    private final ExtractionConfig extractionConfig;

    /** Retrieval configuration: Deep mode, disable Tier1 Insight retrieval */
    private final RetrievalConfig retrievalConfig;

    public MemindAdapter(
            MemoryExtractionPipeline extractor,
            MemoryRetriever retriever,
            ChatClient chatClient,
            BoundaryDetector boundaryDetector,
            ConversationBufferStore bufferStore,
            MemoryStore memoryStore,
            Reranker reranker,
            RetrievalConfig retrievalConfig,
            EvaluationProperties props) {
        this.extractor = extractor;
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.boundaryDetector = boundaryDetector;
        this.bufferStore = bufferStore;
        this.memoryStore = memoryStore;
        this.reranker = reranker;
        this.dualPerspective = props.getSystem().getSearch().isDualPerspective();
        this.extractionConfig = ExtractionConfig.withoutInsight();
        boolean enableInsight = props.getSystem().getMemind().isEnableInsight();
        this.retrievalConfig =
                enableInsight ? retrievalConfig : retrievalConfig.withTier1(TierConfig.disabled());
    }

    /**
     * Runtime override of dual perspective switch, allowing CLI --dual-perspective parameter to be injected at pipeline startup
     *
     * @param dualPerspective true=dual perspective extraction/retrieval, false=single perspective (default)
     */
    public void setDualPerspective(boolean dualPerspective) {
        this.dualPerspective = dualPerspective;
    }

    public void setAddMode(AddMode addMode) {
        this.addMode = addMode;
    }

    @Override
    public String name() {
        return "memind";
    }

    @Override
    public Mono<AddResult> add(AddRequest request) {
        if (addMode == AddMode.CHUNK) {
            if (dualPerspective) {
                return Mono.zip(
                                extractChunkedForSpeaker(request, request.speakerAUserId()),
                                extractChunkedForSpeaker(request, request.speakerBUserId()))
                        .map(tuple -> tuple.getT1().merge(tuple.getT2()));
            }
            return extractChunkedForSpeaker(request, request.speakerAUserId());
        }
        // STREAMING (default)
        if (dualPerspective) {
            // Dual perspective: extract once for speakerA and speakerB
            return Mono.zip(
                            extractForSpeaker(request, request.speakerAUserId()),
                            extractForSpeaker(request, request.speakerBUserId()))
                    .map(tuple -> toAddResult(tuple.getT1()).merge(toAddResult(tuple.getT2())));
        } else {
            // Single perspective (default): extract only from speakerA
            return extractForSpeaker(request, request.speakerAUserId()).map(this::toAddResult);
        }
    }

    /**
     * Stream processing of conversation messages, triggering boundary detection and automatic segmentation extraction through MemoryExtractor#addMessage.
     * After all messages are processed, flush the remaining messages in the buffer.
     * In dual perspective, distinguish USER/ASSISTANT roles by mainUserId, in single perspective unified as USER.
     */
    private Mono<ExtractionResult> extractForSpeaker(AddRequest req, String mainUserId) {
        MemoryId memoryId = DefaultMemoryId.of(mainUserId, AGENT_ID);
        String bufferKey = memoryId.toIdentifier();

        return Flux.fromIterable(req.messages())
                .concatMap(
                        m ->
                                extractor.addMessage(
                                        memoryId, toMessage(m, mainUserId), extractionConfig))
                .collectList()
                .flatMap(
                        intermediateResults -> {
                            // flush remaining buffer
                            List<Message> remaining = bufferStore.load(bufferKey);
                            if (remaining.isEmpty()) {
                                // No remaining messages: return intermediate result summary (or
                                // empty result)
                                if (intermediateResults.isEmpty()) {
                                    return Mono.just(
                                            ExtractionResult.failed(
                                                    memoryId, Duration.ZERO, "empty buffer"));
                                }
                                return Mono.just(intermediateResults.getLast());
                            }
                            bufferStore.clear(bufferKey);
                            var content = new ConversationContent(remaining);
                            var request =
                                    ExtractionRequest.conversation(memoryId, content)
                                            .withConfig(extractionConfig);
                            return extractor.extract(request);
                        })
                .defaultIfEmpty(ExtractionResult.failed(memoryId, Duration.ZERO, "empty buffer"));
    }

    /**
     * CHUNK mode: pass all messages to MemoryExtractor#extract at once, going through the complete three-stage extraction process (RawData → MemoryItem → Insight).
     * In dual perspective, distinguish USER/ASSISTANT roles by mainUserId, in single perspective unified as USER.
     */
    private Mono<AddResult> extractChunkedForSpeaker(AddRequest req, String mainUserId) {
        MemoryId memoryId = DefaultMemoryId.of(mainUserId, AGENT_ID);

        List<Message> messages =
                req.messages().stream().map(em -> toMessage(em, mainUserId)).toList();

        ConversationContent content = new ConversationContent(messages);
        ExtractionRequest extractionRequest =
                ExtractionRequest.conversation(memoryId, content).withConfig(extractionConfig);

        return extractor
                .extract(extractionRequest)
                .map(this::toAddResult)
                .defaultIfEmpty(AddResult.empty());
    }

    @Override
    public Mono<SearchResult> search(SearchRequest request) {
        MemoryId memIdA = DefaultMemoryId.of(request.speakerAUserId(), AGENT_ID);
        RetrievalRequest reqA =
                new RetrievalRequest(
                        memIdA, request.query(), List.of(), retrievalConfig, Map.of(), null, null);

        if (dualPerspective) {
            // Dual perspective: retrieve for speakerA and speakerB, merge and rerank uniformly
            MemoryId memIdB = DefaultMemoryId.of(request.speakerBUserId(), AGENT_ID);
            RetrievalRequest reqB =
                    new RetrievalRequest(
                            memIdB,
                            request.query(),
                            List.of(),
                            retrievalConfig,
                            Map.of(),
                            null,
                            null);
            return Mono.zip(retriever.retrieve(reqA), retriever.retrieve(reqB))
                    .flatMap(
                            tuple ->
                                    mergeRerankAndFormat(
                                            request, memIdA, tuple.getT1(), memIdB, tuple.getT2()));
        } else {
            // Single perspective (default): only retrieve for speakerA
            return retriever
                    .retrieve(reqA)
                    .map(result -> formatSingleResult(request, memIdA, result));
        }
    }

    /** Retrieval result wrapper with source tag */
    private record TaggedItem(ScoredResult scored, String sourceUserId, MemoryId memoryId) {}

    /**
     * Dual perspective: merge the retrieval results of two speakers, rerank uniformly and format output
     */
    private Mono<SearchResult> mergeRerankAndFormat(
            SearchRequest request,
            MemoryId memIdA,
            RetrievalResult resultA,
            MemoryId memIdB,
            RetrievalResult resultB) {
        // insights merge and deduplicate
        List<RetrievalResult.InsightResult> mergedInsights =
                Stream.concat(resultA.insights().stream(), resultB.insights().stream())
                        .filter(distinctByKey(RetrievalResult.InsightResult::text))
                        .toList();

        // rawData merge (deduplicate by caption, keep the one with higher maxScore)
        List<RetrievalResult.RawDataResult> mergedRawData =
                Stream.concat(resultA.rawData().stream(), resultB.rawData().stream())
                        .collect(
                                Collectors.toMap(
                                        RetrievalResult.RawDataResult::caption,
                                        rd -> rd,
                                        (a, b) -> a.maxScore() >= b.maxScore() ? a : b,
                                        LinkedHashMap::new))
                        .values()
                        .stream()
                        .toList();

        // items merge
        String userA = ((DefaultMemoryId) memIdA).userId();
        String userB = ((DefaultMemoryId) memIdB).userId();
        List<TaggedItem> allTagged = new ArrayList<>();
        resultA.items().forEach(sr -> allTagged.add(new TaggedItem(sr, userA, memIdA)));
        resultB.items().forEach(sr -> allTagged.add(new TaggedItem(sr, userB, memIdB)));

        // extract ScoredResult list for rerank
        List<ScoredResult> mergedScored = allTagged.stream().map(TaggedItem::scored).toList();

        // convert rawData captions to ScoredResult for rerank
        List<ScoredResult> captionScored =
                mergedRawData.stream()
                        .filter(rd -> rd.caption() != null && !rd.caption().isBlank())
                        .map(
                                rd ->
                                        new ScoredResult(
                                                ScoredResult.SourceType.RAW_DATA,
                                                rd.rawDataId(),
                                                rd.caption(),
                                                (float) rd.maxScore(),
                                                rd.maxScore()))
                        .toList();

        return Mono.zip(
                        reranker.rerank(request.query(), mergedScored, request.topK()),
                        captionScored.isEmpty()
                                ? Mono.just(List.<ScoredResult>of())
                                : reranker.rerank(request.query(), captionScored, 5))
                .map(
                        tuple -> {
                            List<ScoredResult> reranked = tuple.getT1();
                            List<ScoredResult> rerankedCaptions = tuple.getT2();

                            // Rebuild ordered list with sourceId after rerank
                            Map<String, TaggedItem> bySourceId = new LinkedHashMap<>();
                            allTagged.forEach(
                                    t -> bySourceId.putIfAbsent(t.scored().sourceId(), t));

                            List<TaggedItem> taggedItems =
                                    reranked.stream()
                                            .map(
                                                    sr -> {
                                                        TaggedItem original =
                                                                bySourceId.get(sr.sourceId());
                                                        return original != null
                                                                ? new TaggedItem(
                                                                        sr,
                                                                        original.sourceUserId(),
                                                                        original.memoryId())
                                                                : new TaggedItem(sr, userA, memIdA);
                                                    })
                                            .toList();

                            // Build reranked rawData results
                            Map<String, RetrievalResult.RawDataResult> rawDataById =
                                    mergedRawData.stream()
                                            .collect(
                                                    Collectors.toMap(
                                                            RetrievalResult.RawDataResult
                                                                    ::rawDataId,
                                                            rd -> rd,
                                                            (a, b) -> a));
                            List<RetrievalResult.RawDataResult> updatedRawData =
                                    rerankedCaptions.stream()
                                            .map(sr -> rawDataById.get(sr.sourceId()))
                                            .filter(Objects::nonNull)
                                            .toList();

                            List<ScoredResult> rerankedItems =
                                    taggedItems.stream().map(TaggedItem::scored).toList();

                            RetrievalResult merged =
                                    new RetrievalResult(
                                            rerankedItems,
                                            mergedInsights,
                                            updatedRawData,
                                            List.of(),
                                            resultA.strategy(),
                                            resultA.query());

                            return new SearchResult(
                                    null,
                                    request.conversationId(),
                                    request.query(),
                                    merged,
                                    merged.formattedResult());
                        });
    }

    private static <T> java.util.function.Predicate<T> distinctByKey(
            java.util.function.Function<? super T, ?> keyExtractor) {
        java.util.Set<Object> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    /**
     * Single perspective: directly format output (rerank has been completed internally in retriever)
     */
    private SearchResult formatSingleResult(
            SearchRequest request, MemoryId memId, RetrievalResult result) {
        return new SearchResult(
                null, request.conversationId(), request.query(), result, result.formattedResult());
    }

    @Override
    public Mono<String> answer(String question, String formattedContext, QAPair qaPair) {
        // MC options have been appended to question by AnswerStage, use directly here
        String prompt =
                ANSWER_PROMPT
                        .replace("{context}", formattedContext)
                        .replace("{question}", question);
        return Mono.fromCallable(
                        () -> {
                            String response = chatClient.prompt(prompt).call().content();
                            return extractAnswer(response);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> clean(String conversationId) {
        // TODO: Implement here when memind-core provides deleteByUserId API
        // Currently, memind-core does not have an interface for batch deletion by userId prefix
        return Mono.empty();
    }

    /**
     * Extract the final answer from LLM response:
     * 1. Prioritize extracting the first non-empty text after {@code FINAL ANSWER:} (EverMemOS answer_prompts.py format)
     * 2. Fallback to the last non-empty text
     */
    private String extractAnswer(String response) {
        if (response == null) return "";

        // Priority 1: FINAL ANSWER: format
        if (response.contains("FINAL ANSWER:")) {
            String[] parts = response.split("FINAL ANSWER:", 2);
            if (parts.length > 1) {
                for (String line : parts[1].split("\n")) {
                    line = line.trim();
                    if (!line.isBlank() && !line.startsWith("#")) return line;
                }
            }
        }

        // Priority 2: Last non-empty text
        String[] lines = response.strip().split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) return lines[i].trim();
        }
        return response.trim();
    }

    private AddResult toAddResult(ExtractionResult r) {
        return new AddResult(countNew(r), 0, countInsights(r));
    }

    /**
     * Convert EvalMessage to Message: distinguish USER/ASSISTANT by mainUserId in dual perspective, unified as USER in single perspective.
     * Also append blip_caption (if any).
     */
    private Message toMessage(EvalMessage em, String mainUserId) {
        String content = em.content();
        String caption = em.metadata() != null ? (String) em.metadata().get("blip_caption") : null;
        if (caption != null && !caption.isBlank()) {
            content += " [Shared image: " + caption + "]";
        }

        if (dualPerspective && !mainUserId.equals(em.speakerId())) {
            return Message.assistant(content, em.timestamp()).withUserName(em.speakerName());
        }
        return Message.user(content, em.timestamp(), em.speakerName());
    }

    private int countNew(ExtractionResult r) {
        if (r == null || r.memoryItemResult() == null) return 0;
        return r.memoryItemResult().newCount();
    }

    private int countInsights(ExtractionResult r) {
        if (r == null || r.insightResult() == null) return 0;
        return r.insightResult().totalCount();
    }
}
