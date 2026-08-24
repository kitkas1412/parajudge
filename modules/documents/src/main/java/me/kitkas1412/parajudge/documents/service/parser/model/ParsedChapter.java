package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A {@code Chương}. Articles that sit directly under the chapter (no {@code Mục})
 * are kept in {@link #articles()}; the rest hang off {@link #sections()}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParsedChapter(
        String no,
        int ordinal,
        String title,
        int page,
        List<ParsedSection> sections,
        List<ParsedArticle> articles) {
}
