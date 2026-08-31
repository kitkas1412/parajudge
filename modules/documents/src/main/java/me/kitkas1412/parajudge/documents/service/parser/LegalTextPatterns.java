package me.kitkas1412.parajudge.documents.service.parser;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The structural vocabulary of a Vietnamese legal document.
 *
 * <p>Every pattern is anchored at the start of a <em>block</em> (a paragraph that
 * the assembler has already un-wrapped), which is what keeps the many inline
 * mentions — {@code quy định tại Mục 1 Chương XI}, {@code khoản 2 Điều 169} — from
 * being mistaken for headings. {@code Chương} and {@code Mục} additionally have to
 * be the whole block.
 */
public final class LegalTextPatterns {

    /** {@code Chương I} / {@code Chương 1} — the number is the whole rest of the line. */
    public static final Pattern CHUONG = Pattern.compile("^Chương\\s+([IVXLCDM]+|\\d{1,3})\\s*$");

    /** {@code Mục 1} — likewise a line of its own. */
    public static final Pattern MUC = Pattern.compile("^Mục\\s+(\\d{1,3}[a-zđ]?)\\s*$");

    /** {@code Điều 12. Tiêu đề} — the dot after the number is what separates it from a reference. */
    public static final Pattern DIEU = Pattern.compile("^Điều\\s+(\\d{1,4})\\s*\\.\\s*(.*)$");

    /** {@code 1. …} or an inserted {@code 1a. …}. */
    public static final Pattern KHOAN = Pattern.compile("^(\\d{1,3}[a-zđ]?)\\s*\\.\\s+(.+)$");

    /** {@code a) …} in the Vietnamese ordinal alphabet. */
    public static final Pattern DIEM = Pattern.compile("^([a-zđ]{1,2})\\s*\\)\\s+(.+)$");

    /** Opening quote of a block of law text lifted from another statute. */
    public static final Pattern QUOTED_START = Pattern.compile("^[“\"]");

    /** {@code … của Luật Bảo hiểm xã hội số 58/2014/QH13 …} — the law an amendment targets. */
    public static final Pattern TARGET_LAW = Pattern.compile(
            "(?:của\\s+)?((?:Bộ luật|Luật|Pháp lệnh|Nghị quyết)\\s+[^,;:]*?số\\s+\\d+/\\d{4}/[A-ZĐ0-9]+)");

    /**
     * {@code quy định tại khoản 2 Điều 169} — a mention of another Điều from inside body
     * text. Unlike {@link #DIEU} this is deliberately unanchored and needs no trailing
     * dot: it is looking for references, not headings.
     */
    public static final Pattern ARTICLE_REFERENCE = Pattern.compile("Điều\\s+(\\d{1,4})");

    /** {@code Bộ luật Lao động số 45/2019/QH14} — the code of the document itself. */
    public static final Pattern DOCUMENT_CODE = Pattern.compile("\\b(\\d{1,3}/\\d{4}/[A-ZĐ]{2,}\\d*)\\b");

    /** The Vietnamese ordinal alphabet used for {@code Điểm}: it skips f, j, q… and adds đ. */
    public static final List<String> DIEM_ORDINALS = List.of(
            "a", "b", "c", "d", "đ", "e", "g", "h", "i", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "x", "y");

    private LegalTextPatterns() {
    }

    /** {@code "b"} follows {@code "a"}; {@code null} means "expecting the first point". */
    public static boolean isNextDiem(String previous, String candidate) {
        int index = DIEM_ORDINALS.indexOf(candidate);
        if (index < 0) {
            return false;
        }
        return previous == null ? index == 0 : index == DIEM_ORDINALS.indexOf(previous) + 1;
    }

    /**
     * {@code 2} follows {@code 1}, and so does the inserted {@code 1a}; {@code null}
     * means "expecting the first clause".
     */
    public static boolean isNextKhoan(String previous, String candidate) {
        if (previous == null) {
            return "1".equals(candidate);
        }
        int previousNumber = leadingNumber(previous);
        int candidateNumber = leadingNumber(candidate);
        String previousSuffix = previous.substring(String.valueOf(previousNumber).length());
        String candidateSuffix = candidate.substring(String.valueOf(candidateNumber).length());

        if (candidateNumber == previousNumber + 1 && candidateSuffix.isEmpty()) {
            return true;
        }
        // 1 -> 1a, or 1a -> 1b: same number, next letter of the insertion sequence.
        return candidateNumber == previousNumber
                && !candidateSuffix.isEmpty()
                && isNextDiem(previousSuffix.isEmpty() ? null : previousSuffix, candidateSuffix);
    }

    public static int leadingNumber(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return end == 0 ? -1 : Integer.parseInt(value.substring(0, end));
    }

    /** Chapters are numbered in Roman; the parsed value is only used for ordering. */
    public static int romanToInt(String roman) {
        if (roman.matches("\\d+")) {
            return Integer.parseInt(roman);
        }
        int total = 0;
        int previous = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int value = switch (Character.toUpperCase(roman.charAt(i))) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return total;
    }

    /** Chapter and section titles are set in capitals; body text never is. */
    public static boolean isAllCaps(String text) {
        boolean sawLetter = false;
        for (char c : text.toCharArray()) {
            if (!Character.isLetter(c)) {
                continue;
            }
            sawLetter = true;
            if (Character.isLowerCase(c)) {
                return false;
            }
        }
        return sawLetter;
    }
}
