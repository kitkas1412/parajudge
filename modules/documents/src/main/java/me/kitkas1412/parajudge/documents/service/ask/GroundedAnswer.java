package me.kitkas1412.parajudge.documents.service.ask;

import java.util.List;

/**
 * The shape the model must answer in.
 *
 * <p>{@code citedChunkIds} is a selection, not free text: the model can only name
 * chunks it was shown, and ids it invents are dropped before they reach the caller.
 * {@code answerable} is what lets it decline — a legal assistant that always produces
 * an answer is one that fabricates when the corpus is silent.
 */
public record GroundedAnswer(boolean answerable, String answer, List<Integer> citedChunkIds) {
}
