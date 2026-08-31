package me.kitkas1412.parajudge.documents.service.chunking;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.entity.Section;
import me.kitkas1412.parajudge.documents.service.parser.model.Amendment;
import me.kitkas1412.parajudge.documents.service.parser.model.AmendmentItem;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedArticle;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedClause;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private static final String HOST_LAW = "45/2019/QH14";

    private final Document document =
            new Document(HOST_LAW, "Bộ luật Lao động", null, null, null);
    private final Chapter chapter = new Chapter(document, "III", "HỢP ĐỒNG LAO ĐỘNG");
    private final Section section = new Section(chapter, "1", "GIAO KẾT HỢP ĐỒNG LAO ĐỘNG");

    private Article article(String sourceLaw) {
        return new Article(document, chapter, sourceLaw.equals(HOST_LAW) ? section : null,
                90, "Tiền lương", "Điều 90. Tiền lương", null, sourceLaw);
    }

    /**
     * One token per word. The grouping rules are what these tests are about, so they
     * should not move when {@link TokenEstimator}'s syllable constant is recalibrated
     * against a new embedding model.
     */
    private static final TokenEstimator WORDS = new TokenEstimator() {
        @Override
        public int estimate(String text) {
            return text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;
        }
    };

    /** The context prefix these fixtures produce is 25 words, so a 120-token budget
     *  leaves room for exactly two of the 41-word Khoản below. */
    private ChunkingService service(int maxTokens) {
        return new ChunkingService(maxTokens, WORDS);
    }

    private ParsedArticle parsed(List<String> leadText, List<ParsedClause> clauses) {
        return new ParsedArticle(90, "Tiền lương", 1, "III", "1", leadText, clauses,
                List.of(), "Điều 90. Tiền lương");
    }

    private ParsedClause clause(String no, String text) {
        return new ParsedClause(no, 1, text, List.of());
    }

    /** A Khoản of a known size, worded so that each one is recognisable in the output. */
    private ParsedClause bulky(String no, int syllables) {
        return clause(no, ("khoản" + no + " ").repeat(syllables).strip());
    }

    @Test
    void keepsAShortArticleWhole() {
        Article article = article(HOST_LAW);

        List<Chunk> chunks = new ChunkingService().chunk(article, parsed(
                List.of("Tiền lương là số tiền mà người sử dụng lao động trả cho người lao động."),
                List.of(clause("1", "Mức lương theo công việc."),
                        clause("2", "Người sử dụng lao động phải trả lương đầy đủ."))));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkType()).isEqualTo(ChunkingService.FULL_DIEU);
        assertThat(chunks.get(0).getKhoanRange()).isNull();
        assertThat(chunks.get(0).getContent())
                .contains("1. Mức lương theo công việc.")
                .contains("2. Người sử dụng lao động phải trả lương đầy đủ.");
        assertThat(chunks.get(0).getTokenCount()).isPositive();
    }

    @Test
    void prefixesEveryChunkWithWhereTheTextSits() {
        List<Chunk> chunks = new ChunkingService().chunk(article(HOST_LAW),
                parsed(List.of(), List.of(clause("1", "Mức lương theo công việc."))));

        assertThat(chunks.get(0).getContent()).startsWith(
                "Bộ luật Lao động — Chương III: HỢP ĐỒNG LAO ĐỘNG"
                        + " — Mục 1: GIAO KẾT HỢP ĐỒNG LAO ĐỘNG — Điều 90. Tiền lương\n");
    }

    @Test
    void writesDiemUnderTheirKhoan() {
        List<Chunk> chunks = new ChunkingService().chunk(article(HOST_LAW), parsed(List.of(),
                List.of(new ParsedClause("1", 1, "Người lao động có các quyền sau đây:",
                        List.of(new ParsedPoint("a", 1, "Làm việc, tự do lựa chọn việc làm;"),
                                new ParsedPoint("đ", 1, "Đơn phương chấm dứt hợp đồng lao động."))))));

        assertThat(chunks.get(0).getContent()).contains("""
                1. Người lao động có các quyền sau đây:
                a) Làm việc, tự do lựa chọn việc làm;
                đ) Đơn phương chấm dứt hợp đồng lao động.""");
    }

    @Test
    void splitsALongArticleIntoContiguousGroupsOfWholeKhoan() {
        List<ParsedClause> clauses = List.of(
                bulky("1", 40), bulky("2", 40), bulky("3", 40), bulky("4", 40));

        List<Chunk> chunks = service(120).chunk(article(HOST_LAW),
                parsed(List.of(), clauses));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(c -> ChunkingService.KHOAN_GROUP.equals(c.getChunkType()));
        assertThat(chunks).extracting(Chunk::getKhoanRange).containsExactly("1-2", "3-4");

        // Every Khoản survives exactly once, and none of them is cut in half.
        for (ParsedClause clause : clauses) {
            assertThat(chunks.stream().filter(c -> c.getContent().contains(clause.text())).count())
                    .as("Khoản %s", clause.no())
                    .isEqualTo(1);
        }
    }

    @Test
    void repeatsAShortLeadInIntoEveryChunk() {
        String lead = "Người sử dụng lao động có các nghĩa vụ sau đây:";

        List<Chunk> chunks = service(120).chunk(article(HOST_LAW),
                parsed(List.of(lead), List.of(bulky("1", 40), bulky("2", 40), bulky("3", 40))));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(c -> c.getContent().contains(lead));
    }

    @Test
    void collectsTheDieuAChunkPointsAtButNotItsOwn() {
        List<Chunk> chunks = new ChunkingService().chunk(article(HOST_LAW), parsed(List.of(),
                List.of(clause("1", "Trường hợp quy định tại khoản 2 Điều 169 và Điều 34 của Bộ luật này."),
                        clause("2", "Theo Điều 34 của Bộ luật này."))));

        assertThat(chunks.get(0).getCrossRefs()).containsExactly(34, 169);
    }

    @Test
    void namesTheQuotedStatuteAndDropsItsHeading() {
        String otherLaw = "Luật Bảo hiểm xã hội số 58/2014/QH13";
        AmendmentItem item = new AmendmentItem("a", "sửa đổi", 54, "Điều kiện hưởng lương hưu",
                List.of("“Điều 54. Điều kiện hưởng lương hưu",
                        "1. Người lao động khi nghỉ việc có đủ 20 năm đóng bảo hiểm xã hội.",
                        "2. Người lao động khi nghỉ việc có đủ 15 năm làm nghề nặng nhọc.”"));

        List<Chunk> chunks = new ChunkingService().chunk(article(otherLaw), item);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent())
                .startsWith(otherLaw + " — Điều 90. Tiền lương\n1. Người lao động")
                .doesNotContain("Điều 54. Điều kiện hưởng lương hưu\n")
                .doesNotContain("“", "”");
    }

    @Test
    void splitsAnOversizedKhoanAtDiemBoundaries() {
        List<ParsedPoint> points = List.of(
                new ParsedPoint("a", 1, "điểma ".repeat(40).strip()),
                new ParsedPoint("b", 1, "điểmb ".repeat(40).strip()),
                new ParsedPoint("c", 1, "điểmc ".repeat(40).strip()));
        String head = "Người sử dụng lao động có quyền đơn phương chấm dứt hợp đồng lao động:";

        List<Chunk> chunks = service(120).chunk(article(HOST_LAW), parsed(List.of(),
                List.of(new ParsedClause("1", 1, head, points))));

        assertThat(chunks).hasSizeGreaterThan(1);
        // Both halves stay Khoản 1, and both carry the sentence the Điểm hang from.
        assertThat(chunks).extracting(Chunk::getKhoanRange).containsOnly("1");
        assertThat(chunks).allMatch(c -> c.getContent().contains("1. " + head));
        for (ParsedPoint point : points) {
            assertThat(chunks.stream().filter(c -> c.getContent().contains(point.text())).count())
                    .as("Điểm %s", point.no()).isEqualTo(1);
        }
    }

    @Test
    void chunksDieu219FromItsOwnInstructionsNotTheStatutesItQuotes() {
        Article article = new Article(document, chapter, null, 219,
                "Sửa đổi, bổ sung một số điều của các luật có liên quan đến lao động",
                "Điều 219. …", null, HOST_LAW);
        AmendmentItem item = new AmendmentItem("a", "Sửa đổi, bổ sung Điều 54 như sau:", 54,
                "Điều kiện hưởng lương hưu",
                List.of("“Điều 54. Điều kiện hưởng lương hưu",
                        "1. Người lao động khi nghỉ việc có đủ 20 năm đóng bảo hiểm xã hội.”"));
        ParsedArticle parsed = new ParsedArticle(219, article.getTitle(), 1, "XVII", null,
                List.of(), List.of(),
                List.of(new Amendment("1", "Luật Bảo hiểm xã hội số 58/2014/QH13",
                        "Sửa đổi, bổ sung một số điều của Luật Bảo hiểm xã hội số 58/2014/QH13:",
                        List.of(item))),
                "Điều 219. …");

        List<Chunk> chunks = new ChunkingService().chunk(article, parsed);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent())
                .contains("1. Sửa đổi, bổ sung một số điều của Luật Bảo hiểm xã hội")
                .contains("a) Sửa đổi, bổ sung Điều 54 như sau:")
                .doesNotContain("có đủ 20 năm đóng bảo hiểm xã hội");
    }

    @Test
    void fallsBackToTheArticleTextWhenThereIsNoKhoan() {
        Article article = new Article(document, chapter, section, 1, "Phạm vi điều chỉnh",
                "Điều 1. Phạm vi điều chỉnh\nBộ luật Lao động quy định tiêu chuẩn lao động.",
                null, HOST_LAW);

        List<Chunk> chunks = new ChunkingService().chunk(article,
                new ParsedArticle(1, "Phạm vi điều chỉnh", 1, "I", null, List.of(), List.of(),
                        List.of(), article.getFullText()));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkType()).isEqualTo(ChunkingService.FULL_DIEU);
        assertThat(chunks.get(0).getContent()).endsWith("Bộ luật Lao động quy định tiêu chuẩn lao động.");
    }

    @Test
    void hangsEveryChunkOffItsArticle() {
        Article article = article(HOST_LAW);
        List<Chunk> chunks = new ArrayList<>(service(120).chunk(article,
                parsed(List.of(), List.of(bulky("1", 40), bulky("2", 40), bulky("3", 40)))));

        assertThat(article.getChunks()).containsExactlyElementsOf(chunks);
        assertThat(chunks).allMatch(c -> c.getArticle() == article);
    }
}
