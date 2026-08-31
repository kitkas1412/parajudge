package me.kitkas1412.parajudge.documents.service.ask;

import com.fasterxml.jackson.annotation.JsonInclude;
import me.kitkas1412.parajudge.documents.service.search.ChunkHit;
import me.kitkas1412.parajudge.documents.service.search.ReferencedArticle;

import java.util.List;

/**
 * What the corpus says about a question.
 *
 * <p>{@code retrieved} is returned alongside the prose on purpose: a legal answer that
 * cannot be checked against its sources is worth little, and this is what lets a reader
 * see exactly what the model was shown.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Answer(
        String question,
        boolean answered,
        String answer,
        List<Citation> citations,
        String model,
        List<ChunkHit> retrieved,
        List<ReferencedArticle> referenced,
        long millis) {

    /** Nothing in the corpus came close enough to be worth answering from. */
    static Answer notFound(String question, String message, long millis) {
        return new Answer(question, false, message, List.of(), null, List.of(), List.of(), millis);
    }
}
