package me.kitkas1412.parajudge.persistence;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.repository.ArticleRepository;
import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import me.kitkas1412.parajudge.documents.repository.DocumentRepository;
import me.kitkas1412.parajudge.documents.service.search.ChunkHit;
import me.kitkas1412.parajudge.documents.service.search.SearchQuery;
import me.kitkas1412.parajudge.documents.service.search.SearchResult;
import me.kitkas1412.parajudge.documents.service.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The nearest-neighbour query against a real pgvector.
 *
 * <p>The unit tests mock the repository, so the native SQL — the {@code <=>} operator,
 * the {@code CAST(… AS vector)} of the query literal, the score ordering — is only ever
 * exercised here. The embedding model is stubbed with hand-built unit vectors so the
 * expected similarities are arithmetic rather than a model's opinion, and the test needs
 * no Ollama.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SearchService.class)
@Testcontainers(disabledWithoutDocker = true)
class VectorSearchTest {

    private static final int DIMENSIONS = 1024;
    private static final String HOST_LAW = "45/2019/QH14";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Autowired
    private SearchService search;
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private ChunkRepository chunks;
    @Autowired
    private ArticleRepository articles;

    /** A unit vector pointing along one axis, so cosine similarity is a dot product. */
    private static float[] axis(int index) {
        float[] vector = new float[DIMENSIONS];
        vector[index] = 1.0f;
        return vector;
    }

    /** {@code cos(query, axis(0)) = a} and {@code cos(query, axis(1)) = b}. */
    private static float[] mix(float a, float b) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = a;
        vector[1] = b;
        return vector;
    }

    @BeforeEach
    void seed() {
        Document document = new Document(HOST_LAW, "Bộ luật Lao động", null, null, null);
        Chapter chapter = new Chapter(document, "VII", "THỜI GIỜ NGHỈ NGƠI");
        Article a113 = new Article(document, chapter, null, 113, "Nghỉ hằng năm",
                "Điều 113. Nghỉ hằng năm", null, HOST_LAW);
        Article a169 = new Article(document, chapter, null, 169, "Tuổi nghỉ hưu",
                "Điều 169. Tuổi nghỉ hưu", null, HOST_LAW);

        // Điều 113 khoản 1-5 points at Điều 169; Điều 169 itself points nowhere.
        new Chunk(a113, "khoan_group", "1-5", "Nghỉ hằng năm — nội dung",
                new Integer[]{169}, 100).assignEmbedding(axis(0));
        new Chunk(a169, "full_dieu", null, "Tuổi nghỉ hưu — nội dung",
                new Integer[0], 80).assignEmbedding(axis(1));

        documents.saveAndFlush(document);
    }

    @Test
    void ordersByCosineSimilarityAgainstPgvector() {
        // 0.8 along Điều 113's axis, 0.6 along Điều 169's.
        when(embeddingModel.embed(anyString())).thenReturn(mix(0.8f, 0.6f));

        SearchResult result = search.search(SearchQuery.of("nghỉ phép"));

        assertThat(result.hits()).extracting(ChunkHit::dieuNo).containsExactly(113, 169);
        assertThat(result.hits().get(0).score()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.hits().get(1).score()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.hits().get(0).citation()).isEqualTo("Điều 113 khoản 1-5");
        assertThat(result.hits().get(0).crossRefs()).containsExactly(169);
    }

    @Test
    void dropsAnythingBelowTheFloor() {
        when(embeddingModel.embed(anyString())).thenReturn(mix(0.8f, 0.6f));

        SearchResult result = search.search(new SearchQuery("nghỉ phép", 5, 0.7, false));

        // Điều 169 scores 0.6 and is left out rather than padding the answer.
        assertThat(result.hits()).extracting(ChunkHit::dieuNo).containsExactly(113);
    }

    @Test
    void honoursTopK() {
        when(embeddingModel.embed(anyString())).thenReturn(mix(0.8f, 0.6f));

        assertThat(search.search(new SearchQuery("nghỉ phép", 1, 0.0, false)).hits()).hasSize(1);
    }

    @Test
    void skipsChunksThatHaveNoVectorYet() {
        Article a1 = articles.findAll().get(0);
        new Chunk(a1, "full_dieu", null, "chua embed", new Integer[0], 10);
        articles.saveAndFlush(a1);
        when(embeddingModel.embed(anyString())).thenReturn(mix(0.8f, 0.6f));

        SearchResult result = search.search(new SearchQuery("nghỉ phép", 10, 0.0, false));

        assertThat(chunks.count()).isEqualTo(3);
        assertThat(chunks.countWithoutEmbedding()).isEqualTo(1);
        assertThat(result.hits()).hasSize(2);
    }

    @Test
    void followsCrossReferencesIntoTheHostStatute() {
        when(embeddingModel.embed(anyString())).thenReturn(mix(1.0f, 0.0f));

        SearchResult result = search.search(new SearchQuery("nghỉ phép", 5, 0.5, true));

        assertThat(result.hits()).extracting(ChunkHit::dieuNo).containsExactly(113);
        assertThat(result.referenced()).hasSize(1);
        assertThat(result.referenced().get(0).dieuNo()).isEqualTo(169);
        assertThat(result.referenced().get(0).title()).isEqualTo("Tuổi nghỉ hưu");
        assertThat(result.referenced().get(0).citedBy()).containsExactly(113);
    }
}
