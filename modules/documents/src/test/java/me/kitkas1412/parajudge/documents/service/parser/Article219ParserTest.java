package me.kitkas1412.parajudge.documents.parser;

import me.kitkas1412.parajudge.documents.parser.model.Amendment;
import me.kitkas1412.parajudge.documents.parser.model.AmendmentItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Article219ParserTest {

    /** Điều 219 as it appears in the code, trimmed to the shape that matters. */
    private static final List<String> BLOCKS = List.of(
            "1. Sửa đổi, bổ sung một số điều của Luật Bảo hiểm xã hội số 58/2014/QH13 "
                    + "đã được sửa đổi, bổ sung theo Luật số 84/2015/QH13:",
            "a) Sửa đổi, bổ sung Điều 54 như sau:",
            "“Điều 54. Điều kiện hưởng lương hưu",
            "1. Người lao động quy định tại các điểm a, b, c khoản 1 Điều 2 của Luật này;",
            "a) Đủ tuổi theo quy định tại khoản 2 Điều 169 của Bộ luật Lao động;",
            "4. Điều kiện về tuổi hưởng lương hưu theo quy định của Chính phủ.”;",
            "b) Sửa đổi, bổ sung khoản 1 Điều 73 như sau:",
            "“1. Người lao động hưởng lương hưu khi có đủ các điều kiện sau đây:",
            "b) Đủ 20 năm đóng bảo hiểm xã hội trở lên.”.",
            "2. Sửa đổi, bổ sung Điều 32 của Bộ luật Tố tụng dân sự số 92/2015/QH13 như sau:",
            "a) Sửa đổi, bổ sung tên điều, khoản 1 như sau:",
            "“Điều 32. Những tranh chấp về lao động thuộc thẩm quyền giải quyết của Tòa án",
            "1. Tranh chấp lao động cá nhân giữa người lao động với người sử dụng lao động.”;",
            "b) Bãi bỏ khoản 2 Điều 32.");

    private List<Amendment> parse() {
        Article219Parser parser = new Article219Parser();
        int quoteDepth = 0;
        for (String text : BLOCKS) {
            boolean insideQuote = quoteDepth > 0;
            quoteDepth = Math.max(0, quoteDepth + count(text, '“') - count(text, '”'));
            parser.feed(new TextBlock(83, text, false), insideQuote);
        }
        return parser.build();
    }

    private static int count(String text, char c) {
        return (int) text.chars().filter(ch -> ch == c).count();
    }

    @Test
    void groupsAmendmentsByTargetLaw() {
        List<Amendment> amendments = parse();

        assertThat(amendments).hasSize(2);
        assertThat(amendments.get(0).targetLaw()).isEqualTo("Luật Bảo hiểm xã hội số 58/2014/QH13");
        assertThat(amendments.get(1).targetLaw()).isEqualTo("Bộ luật Tố tụng dân sự số 92/2015/QH13");
    }

    @Test
    void keepsQuotedLawOutOfTheHostNumbering() {
        // The “…” block restarts at khoản 1 / điểm a; those must not become items of
        // the amendment, or Điều 219 would appear to have a điểm a twice over.
        AmendmentItem first = parse().get(0).items().get(0);

        assertThat(first.diemNo()).isEqualTo("a");
        assertThat(first.targetArticleNo()).isEqualTo(54);
        assertThat(first.targetArticleTitle()).isEqualTo("Điều kiện hưởng lương hưu");
        assertThat(first.quotedText()).hasSize(4);
        assertThat(parse().get(0).items()).extracting(AmendmentItem::diemNo).containsExactly("a", "b");
    }

    @Test
    void recordsAmendmentsThatQuoteNoArticleHeading() {
        AmendmentItem clauseOnly = parse().get(0).items().get(1);

        assertThat(clauseOnly.instruction()).startsWith("Sửa đổi, bổ sung khoản 1 Điều 73");
        assertThat(clauseOnly.targetArticleNo()).isNull();
        assertThat(clauseOnly.quotedText()).hasSize(2);
    }

    @Test
    void keepsUnquotedRepealsAsPlainItems() {
        List<AmendmentItem> items = parse().get(1).items();

        assertThat(items).extracting(AmendmentItem::diemNo).containsExactly("a", "b");
        assertThat(items.get(1).instruction()).isEqualTo("Bãi bỏ khoản 2 Điều 32.");
        assertThat(items.get(1).quotedText()).isEmpty();
    }
}
