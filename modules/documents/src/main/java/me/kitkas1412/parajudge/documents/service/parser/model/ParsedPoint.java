package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An {@code Điểm} — {@code a)}, {@code b)}, … in the Vietnamese ordinal alphabet. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParsedPoint(
        String no,
        int page,
        String text) {
}
