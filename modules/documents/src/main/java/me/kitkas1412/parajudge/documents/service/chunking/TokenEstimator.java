package me.kitkas1412.parajudge.documents.service.chunking;

/**
 * How many tokens a piece of Vietnamese text is worth to an embedding model.
 *
 * <p>An estimate, not a count — but a measured one. The first guess of 1.6 pieces per
 * syllable came from what multilingual SentencePiece vocabularies typically do to
 * Vietnamese; running bge-m3's own tokenizer over all 270 chunks of Bộ luật Lao động
 * put the real figure at 1.27 (ratio to the 1.6 estimate: mean 0.795, sd 0.065, range
 * 0.699–1.050 — tight enough that one constant covers the corpus).
 *
 * <p>Re-measure when the embedding model changes. {@code chunks.token_count} is stored
 * for exactly that: the embedding pass reports estimated against actual tokens, so the
 * drift is visible without a separate tokenizer dependency — see {@link
 * me.kitkas1412.parajudge.documents.service.embedding.EmbeddingResult}.
 */
public class TokenEstimator {

    /** Measured against bge-m3 (XLM-R vocabulary) on the full Labour Code. */
    private static final double TOKENS_PER_SYLLABLE = 1.27;

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.strip().split("\\s+").length * TOKENS_PER_SYLLABLE);
    }
}
