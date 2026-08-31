package me.kitkas1412.parajudge.documents.controller;

import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import me.kitkas1412.parajudge.documents.service.embedding.EmbeddingDimensionException;
import me.kitkas1412.parajudge.documents.service.embedding.EmbeddingResult;
import me.kitkas1412.parajudge.documents.service.embedding.EmbeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Drives the embedding pass over {@code chunks}.
 *
 * <p>Separate from {@code /api/parser} on purpose: parsing and ingesting need nothing
 * but the PDF, while this needs a model server, and the two should be able to fail
 * independently.
 */
@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final ChunkRepository chunks;

    public EmbeddingController(EmbeddingService embeddingService, ChunkRepository chunks) {
        this.embeddingService = embeddingService;
        this.chunks = chunks;
    }

    /** {@code GET /api/embeddings} — how much of the corpus is embedded. */
    @GetMapping
    public Map<String, Object> status() {
        long total = chunks.count();
        long pending = chunks.countWithoutEmbedding();
        return Map.of("chunks", total, "embedded", total - pending, "pending", pending);
    }

    /**
     * {@code POST /api/embeddings} — embed everything still missing a vector.
     *
     * @param all also redo chunks that already have one, for when the model changes
     */
    @PostMapping
    public EmbeddingResult embed(@RequestParam(defaultValue = "false") boolean all) {
        return embeddingService.embed(all);
    }

    @ExceptionHandler(EmbeddingDimensionException.class)
    public ResponseEntity<Map<String, Object>> onWrongDimensions(EmbeddingDimensionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "model", e.getModel(),
                "expected", e.getExpected(),
                "actual", e.getActual(),
                "message", e.getMessage(),
                "hint", "Đổi sang model 1024 chiều, hoặc sửa chunks.embedding trong migration mới"));
    }
}
