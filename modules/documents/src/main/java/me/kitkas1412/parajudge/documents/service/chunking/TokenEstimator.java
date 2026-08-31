package me.kitkas1412.parajudge.documents.service.chunking;

/**
 * How many tokens a piece of Vietnamese text is worth to an embedding model.
 *
 * <p>An estimate, not a count. The multilingual SentencePiece vocabularies these
 * models use (XLM-R and its descendants) split a Vietnamese syllable into roughly
 * 1.5–1.7 pieces, so counting whitespace-separated syllables and scaling gets close
 * enough to decide where to cut a Điều. Swap this for the real tokenizer once the
 * embedding model is chosen — {@code chunks.token_count} is stored so the two can be
 * compared on the data already ingested.
 */
public class TokenEstimator {

    private static final double TOKENS_PER_SYLLABLE = 1.6;

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.strip().split("\\s+").length * TOKENS_PER_SYLLABLE);
    }
}
