package me.kitkas1412.parajudge.documents.service.search;

/**
 * What to search for.
 *
 * @param query the question, in natural language
 * @param topK how many chunks to return
 * @param minScore floor on cosine similarity — a legal lookup that answers with the
 *                 least-bad chunk is worse than one that answers with nothing
 * @param expandRefs also return the articles the hits point at, which is what makes an
 *                   answer complete: Điều 54 of the Social Insurance Law sets pension
 *                   conditions by referring to the retirement age in khoản 2 Điều 169
 */
public record SearchQuery(String query, int topK, double minScore, boolean expandRefs) {

    public static final int DEFAULT_TOP_K = 5;

    /** Below this the nearest chunk is usually about something else entirely. */
    public static final double DEFAULT_MIN_SCORE = 0.5;

    public SearchQuery {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query khong duoc rong");
        }
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("topK phai trong khoang 1..50, nhan duoc " + topK);
        }
        if (minScore < 0 || minScore > 1) {
            throw new IllegalArgumentException("minScore phai trong khoang 0..1, nhan duoc " + minScore);
        }
    }

    public static SearchQuery of(String query) {
        return new SearchQuery(query, DEFAULT_TOP_K, DEFAULT_MIN_SCORE, false);
    }
}
