package me.kitkas1412.parajudge.documents.service.ask;

import me.kitkas1412.parajudge.documents.service.search.SearchQuery;

/**
 * A question to answer from the corpus.
 *
 * @param topK how many chunks to put in front of the model
 * @param minScore floor on retrieval — below it the question is treated as unanswerable
 *                 rather than answered from whatever came closest
 * @param expandRefs pull in the articles the hits point at. Off by default: reference
 *                   resolution currently assumes a bare "Điều N" means the host statute,
 *                   which misreads "Điều 2 của Luật này" inside a quoted law, and a wrong
 *                   article in the context becomes a wrong citation in the answer.
 */
public record AskQuery(String question, int topK, double minScore, boolean expandRefs) {

    public static final int DEFAULT_TOP_K = 6;

    public AskQuery {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question khong duoc rong");
        }
    }

    public static AskQuery of(String question) {
        return new AskQuery(question, DEFAULT_TOP_K, SearchQuery.DEFAULT_MIN_SCORE, false);
    }

    public SearchQuery toSearchQuery() {
        return new SearchQuery(question, topK, minScore, expandRefs);
    }
}
