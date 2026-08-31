package me.kitkas1412.parajudge.documents.service.embedding;

/**
 * The model returned vectors of a width {@code chunks.embedding} cannot hold.
 *
 * <p>Thrown before anything is written. Postgres would refuse the insert anyway, but
 * only with the two numbers and no idea which model produced them — and by then part
 * of the batch may already be saved.
 */
public class EmbeddingDimensionException extends RuntimeException {

    private final String model;
    private final int expected;
    private final int actual;

    public EmbeddingDimensionException(String model, int expected, int actual) {
        super("Model %s tra ve vector %d chieu, nhung chunks.embedding la vector(%d)"
                .formatted(model, actual, expected));
        this.model = model;
        this.expected = expected;
        this.actual = actual;
    }

    public String getModel() {
        return model;
    }

    public int getExpected() {
        return expected;
    }

    public int getActual() {
        return actual;
    }
}
