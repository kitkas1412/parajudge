package me.kitkas1412.parajudge.documents.service.search;

import java.util.List;

/**
 * An article pulled in because a hit points at it, not because it matched.
 *
 * <p>Deliberately has no score. It was never compared against the query, so giving it
 * one would invite a caller to rank it alongside the hits; it is supporting context,
 * and {@code citedBy} says which hit needed it.
 */
public record ReferencedArticle(
        Integer articleId,
        Integer dieuNo,
        String title,
        String sourceLaw,
        List<Integer> citedBy,
        String fullText) {
}
