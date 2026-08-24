package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Provenance of a parse run: where the text came from and which pages were skipped.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DocumentMetadata(
        String sourceFile,
        String code,
        String title,
        int totalPages,
        int parsedPages,
        List<Integer> droppedScanPages,
        int chapterCount,
        int articleCount) {
}
