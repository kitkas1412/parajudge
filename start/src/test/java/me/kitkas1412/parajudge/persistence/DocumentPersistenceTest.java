package me.kitkas1412.parajudge.persistence;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.entity.Section;
import me.kitkas1412.parajudge.documents.repository.ArticleRepository;
import me.kitkas1412.parajudge.documents.repository.ChapterRepository;
import me.kitkas1412.parajudge.documents.repository.DocumentRepository;
import me.kitkas1412.parajudge.documents.repository.SectionRepository;
import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;
import me.kitkas1412.parajudge.documents.service.mapper.DocumentEntityMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saves the mapped graph into a real Postgres and reads it back.
 *
 * <p>Worth the container: the mapper builds one object graph and hands it to a
 * single {@code save}, so what is actually being checked here is that the cascade
 * paths reach every entity, that Hibernate inserts parents before the children
 * whose foreign keys point at them, and — through {@code ddl-auto: validate} —
 * that the entities still match {@code V1__create_schema.sql}. None of that is
 * observable in memory.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DocumentPersistenceTest {

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
    private DocumentRepository documents;
    @Autowired
    private ChapterRepository chapters;
    @Autowired
    private SectionRepository sections;
    @Autowired
    private ArticleRepository articles;
    @Autowired
    private EntityManager entityManager;

    private Integer documentId;

    @BeforeEach
    void ingest() throws Exception {
        byte[] pdf;
        try (InputStream in = new ClassPathResource("pdf/boluatlaodong.pdf").getInputStream()) {
            pdf = in.readAllBytes();
        }
        Document document = new DocumentEntityMapper()
                .toEntity(new PdfIngestionService().parse(pdf, "boluatlaodong.pdf"));

        documentId = documents.save(document).getId();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesTheWholeGraphFromOneSave() {
        assertThat(documentId).isNotNull();
        assertThat(chapters.count()).isEqualTo(17);
        assertThat(sections.count()).isEqualTo(24);
        assertThat(articles.count()).isEqualTo(222);
    }

    @Test
    void writesTheDocumentRow() {
        Document saved = documents.findById(documentId).orElseThrow();

        assertThat(saved.getCode()).isEqualTo("45/2019/QH14");
        assertThat(saved.getTitle()).isEqualTo("Bộ luật Lao động");
        assertThat(saved.getIssuedDate()).isEqualTo(LocalDate.of(2019, 11, 20));
        assertThat(saved.getEffectiveDate()).isEqualTo(LocalDate.of(2021, 1, 1));
    }

    @Test
    void roundTripsAmendedByThroughJsonb() {
        Document saved = documents.findById(documentId).orElseThrow();

        assertThat(saved.getAmendedBy()).hasSize(1);
        assertThat(saved.getAmendedBy().get(0).get("code").asText()).isEqualTo("71/2025/QH15");
        assertThat(saved.getAmendedBy().get(0).get("effective_date").asText()).isEqualTo("2026-01-01");
    }

    @Test
    void keepsTheForeignKeysConsistent() {
        Article article = articles.findAll().stream()
                .filter(a -> a.getDieuNo() == 34)
                .filter(a -> "45/2019/QH14".equals(a.getSourceLaw()))
                .findFirst().orElseThrow();

        assertThat(article.getTitle()).isEqualTo("Các trường hợp chấm dứt hợp đồng lao động");
        assertThat(article.getDocument().getId()).isEqualTo(documentId);
        assertThat(article.getChapter().getChapterNo()).isEqualTo("III");
        assertThat(article.getSection()).isNotNull();
        assertThat(article.getSection().getSectionNo()).isEqualTo("3");
        assertThat(article.getSection().getChapter().getId()).isEqualTo(article.getChapter().getId());
    }

    @Test
    void storesArticlesWithNoSection() {
        Article scope = articles.findAll().stream()
                .filter(a -> a.getDieuNo() == 1).findFirst().orElseThrow();

        assertThat(scope.getSection()).isNull();
        assertThat(scope.getChapter().getChapterNo()).isEqualTo("I");
    }

    @Test
    void keepsQuotedStatutesApartBySourceLaw() {
        List<Article> both = articles.findAll().stream()
                .filter(a -> a.getDieuNo() == 54).toList();

        assertThat(both).hasSize(2);
        assertThat(both).extracting(Article::getSourceLaw)
                .containsExactlyInAnyOrder("45/2019/QH14", "Luật Bảo hiểm xã hội số 58/2014/QH13");
    }

    @Test
    void navigatesBackDownTheGraph() {
        Chapter contracts = chapters.findAll().stream()
                .filter(c -> "III".equals(c.getChapterNo())).findFirst().orElseThrow();

        assertThat(contracts.getSections()).extracting(Section::getSectionNo)
                .containsExactlyInAnyOrder("1", "2", "3", "4", "5");
        assertThat(contracts.getArticles()).hasSize(46);
    }
}
