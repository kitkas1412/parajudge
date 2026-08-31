package me.kitkas1412.parajudge.documents.service.search;

import java.util.List;

/**
 * One chunk the query matched.
 *
 * <p>Carries the citation, not just the text: for a legal lookup "Điều 113 khoản 1-5
 * của Bộ luật Lao động" is as much of the answer as the wording is, and it is what
 * lets a reader check the source.
 */
public record ChunkHit(
        Integer chunkId,
        double score,
        Integer articleId,
        Integer dieuNo,
        String khoanRange,
        String articleTitle,
        String sourceLaw,
        String chunkType,
        List<Integer> crossRefs,
        String content) {

    /** {@code Điều 113 khoản 1-5} — how this hit should be cited. */
    public String citation() {
        return khoanRange == null ? "Điều " + dieuNo : "Điều " + dieuNo + " khoản " + khoanRange;
    }
}
