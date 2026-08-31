package me.kitkas1412.parajudge.persistence;

import me.kitkas1412.parajudge.documents.repository.ArticleRepository;
import me.kitkas1412.parajudge.documents.repository.ChapterRepository;
import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import me.kitkas1412.parajudge.documents.repository.DocumentRepository;
import me.kitkas1412.parajudge.documents.repository.SectionRepository;
import me.kitkas1412.parajudge.documents.service.ingestion.DocumentIngestionService;
import me.kitkas1412.parajudge.documents.service.ingestion.DuplicateDocumentException;
import me.kitkas1412.parajudge.documents.service.ingestion.IngestionResult;
import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;
import me.kitkas1412.parajudge.documents.service.mapper.DocumentEntityMapper;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ingesting the full statute into a real Postgres, twice. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DocumentIngestionService.class, DocumentEntityMapper.class})
@Testcontainers(disabledWithoutDocker = true)
class DocumentIngestionServiceTest {

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

    @Autowired
    private DocumentIngestionService ingestion;
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private ChapterRepository chapters;
    @Autowired
    private SectionRepository sections;
    @Autowired
    private ArticleRepository articles;
    @Autowired
    private ChunkRepository chunks;

    private ParsedDocument parsed;

    @BeforeEach
    void parse() throws Exception {
        byte[] pdf;
        try (InputStream in = new ClassPathResource("pdf/boluatlaodong.pdf").getInputStream()) {
            pdf = in.readAllBytes();
        }
        parsed = new PdfIngestionService().parse(pdf, "boluatlaodong.pdf");
    }

    @Test
    void writesTheStatuteAndReportsWhatItWrote() {
        IngestionResult result = ingestion.ingest(parsed, false);

        assertThat(result.documentId()).isNotNull();
        assertThat(result.code()).isEqualTo("45/2019/QH14");
        assertThat(result.title()).isEqualTo("Bộ luật Lao động");
        assertThat(result.chapters()).isEqualTo(17);
        assertThat(result.sections()).isEqualTo(24);
        assertThat(result.articles()).isEqualTo(222);
        assertThat(result.chunks()).isEqualTo(270);
        assertThat(result.parsedPages()).isEqualTo(85);
        assertThat(result.droppedScanPages()).containsExactly(86);
        assertThat(result.replacedExisting()).isFalse();
    }

    @Test
    void refusesASecondCopyOfTheSameStatute() {
        ingestion.ingest(parsed, false);

        assertThatThrownBy(() -> ingestion.ingest(parsed, false))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("45/2019/QH14");
        assertThat(documents.count()).isEqualTo(1);
    }

    @Test
    void replacesInsteadOfDuplicating() {
        Integer first = ingestion.ingest(parsed, false).documentId();

        IngestionResult second = ingestion.ingest(parsed, true);

        assertThat(second.replacedExisting()).isTrue();
        assertThat(second.documentId()).isNotEqualTo(first);
        assertThat(documents.count()).isEqualTo(1);
        // The old rows are gone, not orphaned: articles hold foreign keys to chapters
        // and sections, so a wrong delete order would either fail or leave them behind.
        assertThat(chapters.count()).isEqualTo(17);
        assertThat(sections.count()).isEqualTo(24);
        assertThat(articles.count()).isEqualTo(222);
        assertThat(chunks.count()).isEqualTo(270);
        assertThat(articles.findAll()).allSatisfy(
                a -> assertThat(a.getDocument().getId()).isEqualTo(second.documentId()));
    }

    @Test
    void storesTheChunksPostgresWillBeSearchedOn() {
        ingestion.ingest(parsed, false);

        assertThat(chunks.count()).isEqualTo(270);
        assertThat(chunks.findAll()).allSatisfy(chunk -> {
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getTokenCount()).isPositive();
            assertThat(chunk.getArticle().getId()).isNotNull();
            // vector(1024) stays empty until the embedding pass runs.
            assertThat(chunk.getEmbedding()).isNull();
        });
        // int[] round-trips through Postgres, which is what the GIN index is built on.
        assertThat(chunks.findAll()).anySatisfy(
                chunk -> assertThat(chunk.getCrossRefs()).contains(169));
    }
}
