package me.kitkas1412.parajudge.documents.service.ingestion;

import me.kitkas1412.parajudge.documents.service.parser.model.ParsedArticle;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedChapter;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedClause;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedPoint;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end over the first ten pages of Bộ luật Lao động 45/2019/QH14. */
class PdfIngestionServiceTest {

    private static ParsedDocument document;

    @BeforeAll
    static void parse() throws IOException, URISyntaxException {
        Path pdf = Path.of(Objects.requireNonNull(
                PdfIngestionServiceTest.class.getResource("/pdf/boluatlaodong-trang-1.pdf")).toURI());
        document = new PdfIngestionService().parse(pdf);
    }

    private static ParsedArticle article(int no) {
        return allArticles().stream().filter(a -> a.no() == no).findFirst().orElseThrow();
    }

    private static List<ParsedArticle> allArticles() {
        return document.chapters().stream()
                .flatMap(c -> java.util.stream.Stream.concat(c.articles().stream(),
                        c.sections().stream().flatMap(s -> s.articles().stream())))
                .toList();
    }

    @Test
    void readsTheDocumentHeader() {
        assertThat(document.metadata().code()).isEqualTo("45/2019/QH14");
        assertThat(document.metadata().title()).isEqualTo("BỘ LUẬT LAO ĐỘNG");
        assertThat(document.metadata().parsedPages()).isEqualTo(10);
        assertThat(document.metadata().droppedScanPages()).isEmpty();
    }

    @Test
    void buildsTheChapterAndSectionTree() {
        assertThat(document.chapters()).extracting(ParsedChapter::no).containsExactly("I", "II", "III");
        assertThat(document.chapters().get(0).title()).isEqualTo("NHỮNG QUY ĐỊNH CHUNG");

        ParsedChapter contracts = document.chapters().get(2);
        assertThat(contracts.ordinal()).isEqualTo(3);
        assertThat(contracts.articles()).isEmpty();
        assertThat(contracts.sections()).extracting(ParsedSection::title)
                .containsExactly("GIAO KẾT HỢP ĐỒNG LAO ĐỘNG");
    }

    @Test
    void numbersEveryArticleOnce() {
        assertThat(allArticles()).extracting(ParsedArticle::no)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 24).boxed().toList());
    }

    @Test
    void nestsClausesAndPointsInTheVietnameseOrdinalAlphabet() {
        ParsedArticle rights = article(5);

        assertThat(rights.title()).isEqualTo("Quyền và nghĩa vụ của người lao động");
        assertThat(rights.clauses()).extracting(ParsedClause::no).containsExactly("1", "2");
        assertThat(rights.clauses().get(0).points()).extracting(ParsedPoint::no)
                .containsExactly("a", "b", "c", "d", "đ", "e", "g");
    }

    @Test
    void joinsParagraphsSplitByAPageBreak() {
        // Khoản 9 of Điều 3 starts on page 2 and finishes on page 3.
        ParsedClause definition = article(3).clauses().get(8);

        assertThat(definition.no()).isEqualTo("9");
        assertThat(definition.text())
                .startsWith("Quấy rối tình dục tại nơi làm việc")
                .endsWith("phân công của người sử dụng lao động.")
                .doesNotContain("  ");
    }

    @Test
    void keepsAnArticleWithNoClausesAsLeadText() {
        ParsedArticle scope = article(1);

        assertThat(scope.clauses()).isEmpty();
        assertThat(scope.leadText()).singleElement().asString().startsWith("Bộ luật Lao động quy định");
    }

    @Test
    void doesNotMistakeInlineReferencesForHeadings() {
        // "quy định tại Mục 1 Chương XI của Bộ luật này" sits inside khoản 1 of Điều 3.
        assertThat(article(3).clauses().get(0).text()).contains("Mục 1 Chương XI");
        assertThat(document.chapters()).hasSize(3);
    }

    @Test
    void liftsFootnotesOutOfTheBody() {
        assertThat(document.footnotes()).singleElement().satisfies(footnote -> {
            assertThat(footnote.marker()).isEqualTo("1");
            assertThat(footnote.page()).isEqualTo(1);
            assertThat(footnote.text()).startsWith("Luật Công nghiệp công nghệ số");
        });
        // The superscript marker is stripped from the sentence that referenced it.
        assertThat(document.preamble()).contains("Quốc hội ban hành Bộ luật Lao động.");
    }

    @Test
    void writesHierarchicalJson(@TempDir Path tempDir) throws Exception {
        Path pdf = Path.of(Objects.requireNonNull(
                getClass().getResource("/pdf/boluatlaodong-trang-1.pdf")).toURI());
        Path json = tempDir.resolve("out/boluatlaodong.json");

        new PdfIngestionService().parseToJson(pdf, json);

        assertThat(json).exists();
        assertThat(Files.readString(json))
                .contains("\"chapters\"", "\"sections\"", "\"articles\"", "\"clauses\"", "\"points\"");
    }
}
