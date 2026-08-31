package me.kitkas1412.parajudge.documents.service.ingestion;

import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;

import java.util.List;

/**
 * What an ingest wrote. Deliberately not the tree — the caller has just handed the
 * tree in, and the whole of it is ~550 KB.
 */
public record IngestionResult(
        Integer documentId,
        String code,
        String title,
        int chapters,
        int sections,
        int articles,
        int chunks,
        int parsedPages,
        List<Integer> droppedScanPages,
        boolean replacedExisting) {

    public static IngestionResult of(Document saved, ParsedDocument parsed, boolean replacedExisting) {
        int sections = saved.getChapters().stream().mapToInt(c -> c.getSections().size()).sum();
        int chunks = saved.getArticles().stream().mapToInt(a -> a.getChunks().size()).sum();
        return new IngestionResult(saved.getId(), saved.getCode(), saved.getTitle(),
                saved.getChapters().size(), sections, saved.getArticles().size(), chunks,
                parsed.metadata().parsedPages(), parsed.metadata().droppedScanPages(),
                replacedExisting);
    }
}
