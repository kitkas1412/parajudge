package me.kitkas1412.parajudge.documents.service.embedding;

import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills in {@code chunks.embedding}.
 *
 * <p>A pass of its own rather than part of the ingest: the ingest must not depend on a
 * model server being up, and it must not hold a database connection through however
 * long the vectors take. So chunks are written first with a null vector, and this walks
 * back over them.
 *
 * <p>Deliberately not {@code @Transactional} as a whole. Each batch is saved by its own
 * {@code saveAll}, so a run that fails halfway keeps the work it already did and the
 * next run picks up exactly where it stopped — {@link
 * ChunkRepository#findWithoutEmbedding()} is the resume point.
 */
@Service
public class EmbeddingService {

    /** The width of {@code chunks.embedding}. A model of any other size cannot be stored. */
    public static final int DIMENSIONS = 1024;

    private final EmbeddingModel embeddingModel;
    private final ChunkRepository chunks;
    private final String model;
    private final int batchSize;

    public EmbeddingService(EmbeddingModel embeddingModel, ChunkRepository chunks,
                            @Value("${spring.ai.ollama.embedding.model:bge-m3}") String model,
                            @Value("${parajudge.embedding.batch-size:16}") int batchSize) {
        this.embeddingModel = embeddingModel;
        this.chunks = chunks;
        this.model = model;
        this.batchSize = batchSize;
    }

    /**
     * @param all re-embed chunks that already have a vector, for when the model changes
     *            — vectors from two different models cannot be compared, so switching
     *            model means redoing all of them, not just the missing ones
     */
    public EmbeddingResult embed(boolean all) {
        long start = System.currentTimeMillis();
        List<Chunk> targets = all ? chunks.findAll() : chunks.findWithoutEmbedding();

        int embedded = 0;
        int estimated = 0;
        Integer actual = null;
        for (int from = 0; from < targets.size(); from += batchSize) {
            List<Chunk> batch = targets.subList(from, Math.min(from + batchSize, targets.size()));
            actual = add(actual, apply(batch));
            estimated += batch.stream().mapToInt(Chunk::getTokenCount).sum();
            chunks.saveAll(batch);
            embedded += batch.size();
        }

        return new EmbeddingResult(model, DIMENSIONS, embedded, chunks.countWithoutEmbedding(),
                estimated, actual, drift(estimated, actual),
                System.currentTimeMillis() - start);
    }

    /** @return the model's own token count for this batch, or {@code null} if it reports none */
    private Integer apply(List<Chunk> batch) {
        List<String> contents = batch.stream().map(Chunk::getContent).toList();
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(contents,
                OllamaEmbeddingOptions.builder()
                        .model(model)
                        // Say so rather than silently dropping the tail of a long chunk.
                        .truncate(false)
                        .build()));

        List<Embedding> results = response.getResults();
        if (results.size() != batch.size()) {
            throw new IllegalStateException("Yeu cau %d chunk nhung nhan ve %d vector"
                    .formatted(batch.size(), results.size()));
        }
        List<float[]> vectors = new ArrayList<>(results.size());
        for (Embedding result : results) {
            float[] vector = result.getOutput();
            if (vector.length != DIMENSIONS) {
                throw new EmbeddingDimensionException(model, DIMENSIONS, vector.length);
            }
            vectors.add(vector);
        }
        // Assign only once the whole batch has been checked, so a bad model leaves no
        // half-embedded batch behind.
        for (int i = 0; i < batch.size(); i++) {
            batch.get(i).assignEmbedding(vectors.get(i));
        }
        return promptTokens(response);
    }

    private Integer promptTokens(EmbeddingResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        Integer prompt = response.getMetadata().getUsage().getPromptTokens();
        // Spring AI substitutes an empty Usage when the server reports none, and that
        // reads back as zero. No real embedding call costs zero tokens, so treat it as
        // "not reported" rather than letting it drag the drift figure to nonsense.
        return prompt == null || prompt == 0 ? null : prompt;
    }

    private Integer add(Integer running, Integer batch) {
        if (batch == null) {
            return running;
        }
        return running == null ? batch : running + batch;
    }

    /** How far {@code TokenEstimator} is off: above 1.0 means it is under-counting. */
    private Double drift(int estimated, Integer actual) {
        if (actual == null || estimated == 0) {
            return null;
        }
        return Math.round(actual * 1000.0 / estimated) / 1000.0;
    }
}
