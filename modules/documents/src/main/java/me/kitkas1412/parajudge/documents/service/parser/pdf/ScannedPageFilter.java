package me.kitkas1412.parajudge.documents.parser.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops the scanned pages appended to the end of a consolidated legal document.
 *
 * <p>These pages carry an OCR text layer, so they cannot be found by asking
 * whether text exists — they have to be found by asking whether the text is
 * Vietnamese. Real body text in this corpus runs at roughly 25–35% diacritic
 * letters; an OCR layer over a scan collapses to near zero
 * ({@code lao d{>ng ma Ban tr9ng tai lao d(>ng}), which is what the threshold here
 * keys on.
 *
 * <p>Only the trailing run is removed: a bad page in the middle is far more
 * likely to be a parser problem than a scan, and silently dropping it would lose
 * articles.
 */
public final class ScannedPageFilter {

    /** Below this share of Vietnamese-specific letters the text is not Vietnamese. */
    private static final double MIN_DIACRITIC_RATIO = 0.10;

    /** Too little text to judge by ratio; treat as a scan only if it is also near-empty. */
    private static final int MIN_LETTERS_FOR_RATIO = 200;

    private static final String VIETNAMESE_LETTERS =
            "ăâđêôơư"
            + "àáảãạằắẳẵặầấẩẫậ"
            + "èéẻẽẹềếểễệ"
            + "ìíỉĩị"
            + "òóỏõọồốổỗộờớởỡợ"
            + "ùúủũụừứửữự"
            + "ỳýỷỹỵ";

    public record Result(List<PageText> pages, List<Integer> droppedPages) {
    }

    public Result removeTrailingScans(List<PageText> pages) {
        int last = pages.size();
        while (last > 0 && isScan(pages.get(last - 1))) {
            last--;
        }
        List<Integer> dropped = new ArrayList<>();
        for (int i = last; i < pages.size(); i++) {
            dropped.add(pages.get(i).pageNo());
        }
        return new Result(List.copyOf(pages.subList(0, last)), List.copyOf(dropped));
    }

    public boolean isScan(PageText page) {
        String text = page.bodyAsText();
        int letters = 0;
        int vietnamese = 0;
        for (char c : text.toLowerCase().toCharArray()) {
            if (!Character.isLetter(c)) {
                continue;
            }
            letters++;
            if (VIETNAMESE_LETTERS.indexOf(c) >= 0) {
                vietnamese++;
            }
        }
        if (letters < MIN_LETTERS_FOR_RATIO) {
            // An empty or nearly empty trailing page is dropped as well.
            return letters < 20;
        }
        return (double) vietnamese / letters < MIN_DIACRITIC_RATIO;
    }
}
