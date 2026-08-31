package me.kitkas1412.parajudge.documents.service.embedding;

/**
 * What an embedding pass did.
 *
 * <p>{@code estimatedTokens} is what {@link
 * me.kitkas1412.parajudge.documents.service.chunking.TokenEstimator} predicted for the
 * text that was sent; {@code actualTokens} is what the model's own tokenizer counted.
 * The two are reported side by side so the estimator can be calibrated against real
 * data instead of guessed at — that is the whole reason {@code chunks.token_count}
 * exists. {@code actualTokens} is {@code null} when the server reports no usage.
 */
public record EmbeddingResult(
        String model,
        int dimensions,
        int embedded,
        long remaining,
        int estimatedTokens,
        Integer actualTokens,
        Double tokensPerEstimate,
        long millis) {
}
