package me.kitkas1412.parajudge.documents.service.ask;

/**
 * A source the answer rests on.
 *
 * <p>Built from the retrieved chunk, never from the model's output. The model only
 * picks which chunk ids it used; every field here comes from the database, so a
 * citation cannot name an article that does not exist or misquote its number.
 */
public record Citation(
        Integer chunkId,
        Integer dieuNo,
        String khoanRange,
        String articleTitle,
        String sourceLaw,
        String reference) {
}
