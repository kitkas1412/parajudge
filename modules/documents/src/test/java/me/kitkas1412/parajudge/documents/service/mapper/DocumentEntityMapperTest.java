package me.kitkas1412.parajudge.documents.service.mapper;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.entity.Section;
import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Maps the whole of Bộ luật Lao động 45/2019/QH14, in memory — no database. */
class DocumentEntityMapperTest {

    private static final String HOST_LAW = "45/2019/QH14";
    private static final String SOCIAL_INSURANCE_LAW = "Luật Bảo hiểm xã hội số 58/2014/QH13";

    private static Document document;

    @BeforeAll
    static void map() throws Exception {
        Path pdf = Path.of(Objects.requireNonNull(
                DocumentEntityMapperTest.class.getResource("/pdf/boluatlaodong.pdf")).toURI());
        document = new DocumentEntityMapper().toEntity(new PdfIngestionService().parse(pdf));
    }

    private static List<Article> articlesOf(String sourceLaw) {
        return document.getArticles().stream().filter(a -> sourceLaw.equals(a.getSourceLaw())).toList();
    }

    @Test
    void fillsTheDocumentRowFromThePreamble() {
        assertThat(document.getCode()).isEqualTo(HOST_LAW);
        assertThat(document.getTitle()).isEqualTo("Bộ luật Lao động");
        assertThat(document.getIssuedDate()).isEqualTo(LocalDate.of(2019, 11, 20));
        assertThat(document.getEffectiveDate()).isEqualTo(LocalDate.of(2021, 1, 1));
    }

    @Test
    void recordsTheAmendingLawAsJson() {
        assertThat(document.getAmendedBy()).hasSize(1);
        assertThat(document.getAmendedBy().get(0).get("code").asText()).isEqualTo("71/2025/QH15");
        assertThat(document.getAmendedBy().get(0).get("effective_date").asText()).isEqualTo("2026-01-01");
    }

    @Test
    void buildsTheChapterAndSectionTree() {
        assertThat(document.getChapters()).hasSize(17);
        assertThat(document.getChapters().stream().mapToInt(c -> c.getSections().size()).sum())
                .isEqualTo(24);

        Chapter contracts = document.getChapters().get(2);
        assertThat(contracts.getChapterNo()).isEqualTo("III");
        assertThat(contracts.getTitle()).isEqualTo("HỢP ĐỒNG LAO ĐỘNG");
        assertThat(contracts.getSections()).extracting(Section::getSectionNo)
                .containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void attachesEveryArticleToItsChapterAndSection() {
        List<Article> own = articlesOf(HOST_LAW);

        assertThat(own).hasSize(219);
        assertThat(own).extracting(Article::getDieuNo)
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 219).boxed().toList());
        assertThat(own).allSatisfy(article -> {
            assertThat(article.getDocument()).isSameAs(document);
            assertThat(article.getChapter()).isNotNull();
            assertThat(article.getTitle()).isNotBlank();
            assertThat(article.getFullText()).isNotBlank();
        });
        // 158 sit under a Mục; the rest hang straight off their chapter.
        assertThat(own.stream().filter(a -> a.getSection() != null)).hasSize(158);
    }

    @Test
    void bothSidesOfEveryAssociationAgree() {
        for (Chapter chapter : document.getChapters()) {
            assertThat(chapter.getDocument()).isSameAs(document);
            for (Section section : chapter.getSections()) {
                assertThat(section.getChapter()).isSameAs(chapter);
                assertThat(section.getArticles()).allSatisfy(
                        a -> assertThat(a.getSection()).isSameAs(section));
            }
            assertThat(chapter.getArticles()).allSatisfy(
                    a -> assertThat(a.getChapter()).isSameAs(chapter));
        }
    }

    @Test
    void storesTheStatutesQuotedByDieu219AsArticlesOfTheirOwn() {
        // source_law is what tells them apart from the host code's own articles.
        Article amended = articlesOf(SOCIAL_INSURANCE_LAW).stream()
                .filter(a -> a.getDieuNo() == 54).findFirst().orElseThrow();

        assertThat(amended.getTitle()).isEqualTo("Điều kiện hưởng lương hưu");
        assertThat(amended.getFullText()).contains("Điều kiện về tuổi hưởng lương hưu");
        assertThat(amended.getSection()).isNull();
        assertThat(amended.getChapter().getChapterNo()).isEqualTo("XVII");
    }

    @Test
    void doesNotLetQuotedArticlesCollideWithTheHostCode() {
        // Điều 54 exists in both the Labour Code and the quoted Social Insurance Law.
        List<Article> both = document.getArticles().stream()
                .filter(a -> a.getDieuNo() == 54).toList();

        assertThat(both).hasSize(2);
        assertThat(both).extracting(Article::getSourceLaw)
                .containsExactlyInAnyOrder(HOST_LAW, SOCIAL_INSURANCE_LAW);
        assertThat(both).extracting(Article::getTitle)
                .containsExactlyInAnyOrder("Doanh nghiệp cho thuê lại lao động",
                        "Điều kiện hưởng lương hưu");
    }

    @Test
    void skipsAmendmentsThatRewriteOnlyAClause() {
        // "Sửa đổi, bổ sung khoản 1 Điều 73" names no whole article, so there is
        // nothing to store as one; the text stays inside Điều 219.
        assertThat(document.getArticles()).hasSize(219 + 3);
        assertThat(articlesOf(SOCIAL_INSURANCE_LAW)).extracting(Article::getDieuNo)
                .containsExactlyInAnyOrder(54, 55);
    }
}
