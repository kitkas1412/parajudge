package me.kitkas1412.parajudge.documents.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalTextPatternsTest {

    @Test
    void matchesHeadings() {
        assertThat(LegalTextPatterns.CHUONG.matcher("Chương XVII").matches()).isTrue();
        assertThat(LegalTextPatterns.MUC.matcher("Mục 1").matches()).isTrue();
        assertThat(LegalTextPatterns.DIEU.matcher("Điều 219. Sửa đổi, bổ sung").matches()).isTrue();
        assertThat(LegalTextPatterns.KHOAN.matcher("1a. Tranh chấp lao động").matches()).isTrue();
        assertThat(LegalTextPatterns.DIEM.matcher("đ) Đơn phương chấm dứt").matches()).isTrue();
    }

    @Test
    void ignoresInlineReferences() {
        assertThat(LegalTextPatterns.MUC.matcher("quy định tại Mục 1 Chương XI của Bộ luật này").matches())
                .isFalse();
        assertThat(LegalTextPatterns.CHUONG.matcher("Chương trình an toàn, vệ sinh lao động").matches())
                .isFalse();
        // A reference has no dot after the number, which is what separates it from a heading.
        assertThat(LegalTextPatterns.DIEU.matcher("Điều 169 của Bộ luật Lao động").matches()).isFalse();
    }

    @Test
    void followsTheVietnameseOrdinalAlphabet() {
        assertThat(LegalTextPatterns.isNextDiem("d", "đ")).isTrue();
        assertThat(LegalTextPatterns.isNextDiem("e", "g")).isTrue();
        assertThat(LegalTextPatterns.isNextDiem("e", "f")).isFalse();
        assertThat(LegalTextPatterns.isNextDiem(null, "a")).isTrue();
        assertThat(LegalTextPatterns.isNextDiem(null, "b")).isFalse();
    }

    @Test
    void acceptsInsertedClauses() {
        assertThat(LegalTextPatterns.isNextKhoan("1", "2")).isTrue();
        assertThat(LegalTextPatterns.isNextKhoan("1", "1a")).isTrue();
        assertThat(LegalTextPatterns.isNextKhoan("1a", "1b")).isTrue();
        assertThat(LegalTextPatterns.isNextKhoan("1", "5")).isFalse();
    }

    @Test
    void readsRomanChapterNumbers() {
        assertThat(LegalTextPatterns.romanToInt("XVII")).isEqualTo(17);
        assertThat(LegalTextPatterns.romanToInt("IX")).isEqualTo(9);
    }
}
