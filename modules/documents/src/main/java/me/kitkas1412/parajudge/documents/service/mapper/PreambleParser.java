package me.kitkas1412.parajudge.documents.service.mapper;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the statutes and dates out of the preamble.
 *
 * <p>A consolidated document opens by naming itself and then every law that has
 * amended it, each on one line and each in the same shape:
 *
 * <pre>
 * Bộ luật Lao động số 45/2019/QH14 ngày 20 tháng 11 năm 2019 của Quốc hội,
 * có hiệu lực kể từ ngày 01 tháng 01 năm 2021, được sửa đổi, bổ sung bởi:
 * Luật Công nghiệp công nghệ số số 71/2025/QH15 ngày 14 tháng 6 năm 2025 của
 * Quốc hội, có hiệu lực kể từ ngày 01 tháng 01 năm 2026.
 * </pre>
 *
 * <p>These lines are the only place the issue and effective dates appear, so
 * without reading them {@code documents.issued_date}, {@code effective_date} and
 * {@code amended_by} would all stay empty.
 */
public class PreambleParser {

    /**
     * The title runs up to the last {@code số} before the number — which matters,
     * because {@code Luật Công nghiệp công nghệ số} ends in that same word.
     */
    private static final Pattern LAW_LINE = Pattern.compile(
            "^(.*)\\s+số\\s+(\\d{1,3}/\\d{4}/[A-ZĐ]{2,}\\d*)");

    private static final Pattern DATE = Pattern.compile(
            "ngày\\s+(\\d{1,2})\\s+tháng\\s+(\\d{1,2})\\s+năm\\s+(\\d{4})");

    private static final Pattern EFFECTIVE = Pattern.compile(
            "có hiệu lực(?:\\s+thi hành)?\\s+kể từ\\s+" + DATE.pattern());

    /** @return the statutes in the order they appear; the first one is the document itself */
    public List<LawReference> parse(List<String> preamble) {
        List<LawReference> laws = new ArrayList<>();
        for (String line : preamble) {
            Matcher law = LAW_LINE.matcher(line);
            if (!law.find()) {
                continue;
            }
            LocalDate effective = effectiveDate(line);
            laws.add(new LawReference(law.group(1).strip(), law.group(2),
                    issuedDate(line), effective));
        }
        return List.copyOf(laws);
    }

    /** The promulgation date is the one stated before the "có hiệu lực" clause. */
    private LocalDate issuedDate(String line) {
        int effectiveAt = effectiveClauseStart(line);
        Matcher dates = DATE.matcher(line);
        while (dates.find()) {
            if (effectiveAt < 0 || dates.start() < effectiveAt) {
                return toDate(dates);
            }
        }
        return null;
    }

    private LocalDate effectiveDate(String line) {
        Matcher matcher = EFFECTIVE.matcher(line);
        return matcher.find() ? toDate(matcher) : null;
    }

    private int effectiveClauseStart(String line) {
        Matcher matcher = EFFECTIVE.matcher(line);
        return matcher.find() ? matcher.start() : -1;
    }

    /** Groups 1..3 of {@link #DATE} are day, month, year. */
    private LocalDate toDate(Matcher matcher) {
        try {
            return LocalDate.of(Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(1)));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }
}
