package me.kitkas1412.parajudge.documents.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Root of the hierarchical representation of a Vietnamese legal document
 * (Chương &gt; Mục &gt; Điều &gt; Khoản &gt; Điểm).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParsedDocument(
        DocumentMetadata metadata,
        List<String> preamble,
        List<ParsedChapter> chapters,
        List<Footnote> footnotes) {
}
