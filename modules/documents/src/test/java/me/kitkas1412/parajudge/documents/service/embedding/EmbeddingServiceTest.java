package me.kitkas1412.parajudge.documents.service.embedding;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The embedding pass, with a stand-in for the model server. */
class EmbeddingServiceTest {

    private static final String MODEL = "bge-m3";

    private final ChunkRepository repository = mock(ChunkRepository.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    private final Document document = new Document("45/2019/QH14", "Bộ luật Lao động", null, null, null);
    private final Chapter chapter = new Chapter(document, "VI", "TIỀN LƯƠNG");
    private final Article article = new Article(document, chapter, null, 90, "Tiền lương",
            "Điều 90. Tiền lương", null, "45/2019/QH14");

    private List<Chunk> pending;

    @BeforeEach
    void chunks() {
        pending = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Chunk(article, "khoan_group", String.valueOf(i),
                        "Bộ luật Lao động — Điều 90\n" + i + ". Mức lương theo công việc.",
                        new Integer[0], 20))
                .toList();
        when(repository.findWithoutEmbedding()).thenReturn(pending);
        when(repository.countWithoutEmbedding()).thenReturn(0L);
    }

    private void answerWith(int dimensions, Integer promptTokens) {
        when(embeddingModel.call(any())).thenAnswer(invocation -> {
            EmbeddingRequest request = invocation.getArgument(0);
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                results.add(new Embedding(new float[dimensions], i));
            }
            return promptTokens == null
                    ? new EmbeddingResponse(results)
                    : new EmbeddingResponse(results,
                            new EmbeddingResponseMetadata(MODEL, usage(promptTokens)));
        });
    }

    private Usage usage(int promptTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        return usage;
    }

    @Test
    void fillsInAVectorForEveryChunkThatLacksOne() {
        answerWith(EmbeddingService.DIMENSIONS, 30);

        EmbeddingResult result = service(16).embed(false);

        assertThat(result.embedded()).isEqualTo(5);
        assertThat(result.remaining()).isZero();
        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.dimensions()).isEqualTo(1024);
        assertThat(pending).allSatisfy(chunk ->
                assertThat(chunk.getEmbedding()).hasSize(1024));
        verify(repository).saveAll(any());
    }

    @Test
    void sendsChunksInBatchesRatherThanOneRequestEach() {
        answerWith(EmbeddingService.DIMENSIONS, 12);

        service(2).embed(false);

        // 5 chunks at 2 per batch is 3 requests, not 5.
        verify(embeddingModel, times(3)).call(any());
        verify(repository, times(3)).saveAll(any());
    }

    @Test
    void reportsHowFarTheTokenEstimatorIsOff() {
        // Each chunk was estimated at 20 tokens; the model counts 25 per chunk.
        answerWith(EmbeddingService.DIMENSIONS, 125);

        EmbeddingResult result = service(16).embed(false);

        assertThat(result.estimatedTokens()).isEqualTo(100);
        assertThat(result.actualTokens()).isEqualTo(125);
        assertThat(result.tokensPerEstimate()).isEqualTo(1.25);
    }

    @Test
    void leavesTheDriftUnreportedWhenTheServerSendsNoUsage() {
        answerWith(EmbeddingService.DIMENSIONS, null);

        EmbeddingResult result = service(16).embed(false);

        assertThat(result.embedded()).isEqualTo(5);
        assertThat(result.actualTokens()).isNull();
        assertThat(result.tokensPerEstimate()).isNull();
    }

    @Test
    void refusesAModelWhoseVectorsDoNotFitTheColumn() {
        answerWith(768, 30);

        assertThatThrownBy(() -> service(16).embed(false))
                .isInstanceOf(EmbeddingDimensionException.class)
                .hasMessageContaining("768")
                .hasMessageContaining("1024");

        // Nothing was written, and no chunk was left holding a vector of the wrong width.
        verify(repository, times(0)).saveAll(any());
        assertThat(pending).allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNull());
    }

    @Test
    void refusesAResponseThatDoesNotLineUpWithTheRequest() {
        when(embeddingModel.call(any())).thenReturn(new EmbeddingResponse(
                List.of(new Embedding(new float[EmbeddingService.DIMENSIONS], 0))));

        assertThatThrownBy(() -> service(16).embed(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5")
                .hasMessageContaining("1");
    }

    private EmbeddingService service(int batchSize) {
        return new EmbeddingService(embeddingModel, repository, MODEL, batchSize);
    }
}
