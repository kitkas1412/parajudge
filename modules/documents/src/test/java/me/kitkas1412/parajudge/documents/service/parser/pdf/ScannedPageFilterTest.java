package me.kitkas1412.parajudge.documents.service.parser.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ScannedPageFilterTest {

    private static final String VIETNAMESE =
            "Người sử dụng lao động sử dụng dưới 10 người lao động thực hiện quy định "
            + "của Bộ luật này nhưng được miễn, giảm một số thủ tục theo quy định của Chính phủ. ";

    /** The OCR layer of the scan appended to the end of the consolidated document. */
    private static final String OCR_GARBAGE =
            "lao d{>ng ma Ban tr9ng tai lao d(>ng khong duqc thanh l~p, Ban tr9ng tai lao d()ng "
            + "khong ra quy8t dinh giai quySt tranh chdp ho~c m{>t trong cac hen khong thi hanh. ";

    private PageText page(int no, String text, int repeats) {
        List<TextLine> lines = IntStream.range(0, repeats)
                .mapToObj(i -> new TextLine(no, text, 85f, 100f + i, 14f, false, null))
                .toList();
        return new PageText(no, lines, List.of(), 14f, 85f);
    }

    @Test
    void dropsTheTrailingScan() {
        ScannedPageFilter.Result result = new ScannedPageFilter().removeTrailingScans(List.of(
                page(1, VIETNAMESE, 4),
                page(2, VIETNAMESE, 4),
                page(3, OCR_GARBAGE, 4)));

        assertThat(result.droppedPages()).containsExactly(3);
        assertThat(result.pages()).extracting(PageText::pageNo).containsExactly(1, 2);
    }

    @Test
    void dropsTheEmptyPageBehindTheScan() {
        ScannedPageFilter.Result result = new ScannedPageFilter().removeTrailingScans(List.of(
                page(1, VIETNAMESE, 4),
                page(2, OCR_GARBAGE, 4),
                page(3, "", 1)));

        assertThat(result.droppedPages()).containsExactly(2, 3);
    }

    @Test
    void keepsABadPageInTheMiddle() {
        // Losing articles is worse than keeping a page the heuristic dislikes, so only
        // the trailing run is removed.
        ScannedPageFilter.Result result = new ScannedPageFilter().removeTrailingScans(List.of(
                page(1, VIETNAMESE, 4),
                page(2, OCR_GARBAGE, 4),
                page(3, VIETNAMESE, 4)));

        assertThat(result.droppedPages()).isEmpty();
        assertThat(result.pages()).hasSize(3);
    }
}
