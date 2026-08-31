package me.kitkas1412.parajudge.documents.service.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What a search found. {@code referenced} is empty unless the query asked for the
 * cross-references to be followed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SearchResult(
        String query,
        String model,
        int candidates,
        List<ChunkHit> hits,
        List<ReferencedArticle> referenced,
        long millis) {
}
