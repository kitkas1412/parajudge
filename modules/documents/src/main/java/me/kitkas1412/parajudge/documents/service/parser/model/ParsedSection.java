package me.kitkas1412.parajudge.documents.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** A {@code Mục} inside a {@code Chương}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParsedSection(
        String no,
        String title,
        int page,
        List<ParsedArticle> articles) {
}
