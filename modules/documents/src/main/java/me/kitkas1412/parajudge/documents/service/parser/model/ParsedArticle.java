package me.kitkas1412.parajudge.documents.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * An {@code Điều}.
 *
 * <p>{@code leadText} holds the paragraphs between the heading and the first
 * {@code Khoản}; {@code amendments} is only populated for the nested-law article
 * (Điều 219), whose quoted blocks are <em>not</em> parsed as ordinary clauses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParsedArticle(
        int no,
        String title,
        int page,
        String chapterNo,
        String sectionNo,
        List<String> leadText,
        List<ParsedClause> clauses,
        List<Amendment> amendments,
        String fullText) {
}
