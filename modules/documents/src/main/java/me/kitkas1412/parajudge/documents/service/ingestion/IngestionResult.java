package me.kitkas1412.parajudge.documents.service.ingestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.service.embedding.EmbeddingResult;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;

import java.util.List;

/**
 * What an ingest wrote. Deliberately not the tree — the caller has just handed the
 * tree in, and the whole of it is ~550 KB.
 *
 * <p>{@code embedding} is filled in when the ingest was asked to embed as well. It
 * stays {@code null} when embedding was skipped, and {@code embeddingError} carries
 * the reason when it was attempted and failed — the statute is committed either way,
 * so a model server that is down must not turn a successful ingest into an error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        boolean replacedExisting,
        EmbeddingResult embedding,
        String embeddingError) {

    public static IngestionResult of(Document saved, ParsedDocument parsed, boolean replacedExisting) {
        int sections = saved.getChapters().stream().mapToInt(c -> c.getSections().size()).sum();
        int chunks = saved.getArticles().stream().mapToInt(a -> a.getChunks().size()).sum();
        return new IngestionResult(saved.getId(), saved.getCode(), saved.getTitle(),
                saved.getChapters().size(), sections, saved.getArticles().size(), chunks,
                parsed.metadata().parsedPages(), parsed.metadata().droppedScanPages(),
                replacedExisting, null, null);
    }

    public IngestionResult withEmbedding(EmbeddingResult embedding) {
        return new IngestionResult(documentId, code, title, chapters, sections, articles, chunks,
                parsedPages, droppedScanPages, replacedExisting, embedding, null);
    }

    public IngestionResult withEmbeddingError(String message) {
        return new IngestionResult(documentId, code, title, chapters, sections, articles, chunks,
                parsedPages, droppedScanPages, replacedExisting, null, message);
    }
}
