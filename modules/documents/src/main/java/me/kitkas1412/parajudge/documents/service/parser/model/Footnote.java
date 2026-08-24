package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A page-bottom footnote, keyed by the superscript marker that referenced it. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Footnote(
        String marker,
        int page,
        String text) {
}
