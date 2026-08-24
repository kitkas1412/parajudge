package me.kitkas1412.parajudge.documents.service.mapper;

import java.time.LocalDate;

/**
 * One statute named in the preamble — the document itself, or a law that amended it.
 *
 * @param title        name without the number, e.g. {@code Bộ luật Lao động}
 * @param code         e.g. {@code 45/2019/QH14}
 * @param issuedDate   ngày ban hành
 * @param effectiveDate ngày có hiệu lực
 */
public record LawReference(
        String title,
        String code,
        LocalDate issuedDate,
        LocalDate effectiveDate) {
}
