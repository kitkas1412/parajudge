package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One {@code Điểm} of an {@link Amendment}: the instruction line plus the quoted
 * replacement text lifted out of the “…” block.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AmendmentItem(
        String diemNo,
        String instruction,
        Integer targetArticleNo,
        String targetArticleTitle,
        List<String> quotedText) {
}
