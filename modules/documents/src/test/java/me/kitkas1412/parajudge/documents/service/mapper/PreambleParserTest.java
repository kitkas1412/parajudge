package me.kitkas1412.parajudge.documents.service.mapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreambleParserTest {

    private static final List<String> PREAMBLE = List.of(
            "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM",
            "Độc lập - Tự do - Hạnh phúc",
            "BỘ LUẬT",
            "LAO ĐỘNG",
            "Bộ luật Lao động số 45/2019/QH14 ngày 20 tháng 11 năm 2019 của Quốc hội, "
                    + "có hiệu lực kể từ ngày 01 tháng 01 năm 2021, được sửa đổi, bổ sung bởi:",
            "Luật Công nghiệp công nghệ số số 71/2025/QH15 ngày 14 tháng 6 năm 2025 của Quốc hội, "
                    + "có hiệu lực kể từ ngày 01 tháng 01 năm 2026.",
            "Căn cứ Hiến pháp nước Cộng hòa xã hội chủ nghĩa Việt Nam;",
            "Quốc hội ban hành Bộ luật Lao động.");

    @Test
    void readsTheDocumentAndItsAmendingLaws() {
        List<LawReference> laws = new PreambleParser().parse(PREAMBLE);

        assertThat(laws).containsExactly(
                new LawReference("Bộ luật Lao động", "45/2019/QH14",
                        LocalDate.of(2019, 11, 20), LocalDate.of(2021, 1, 1)),
                new LawReference("Luật Công nghiệp công nghệ số", "71/2025/QH15",
                        LocalDate.of(2025, 6, 14), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void keepsTheWordSoWhenItBelongsToTheName() {
        // "Luật Công nghiệp công nghệ số" ends in the same word that introduces the
        // number, so the title has to run to the last "số", not the first.
        List<LawReference> laws = new PreambleParser().parse(PREAMBLE);

        assertThat(laws.get(1).title()).isEqualTo("Luật Công nghiệp công nghệ số");
    }

    @Test
    void tellsTheIssueDateFromTheEffectiveDate() {
        // Both are written "ngày D tháng M năm Y"; only the clause around them differs.
        LawReference law = new PreambleParser().parse(PREAMBLE).get(0);

        assertThat(law.issuedDate()).isEqualTo(LocalDate.of(2019, 11, 20));
        assertThat(law.effectiveDate()).isEqualTo(LocalDate.of(2021, 1, 1));
    }

    @Test
    void ignoresPreambleLinesThatNameNoStatute() {
        assertThat(new PreambleParser().parse(List.of(
                "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM",
                "Căn cứ Hiến pháp nước Cộng hòa xã hội chủ nghĩa Việt Nam;"))).isEmpty();
    }
}
