package me.kitkas1412.parajudge.documents.service.search;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.repository.ArticleRepository;
import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Finds the chunks that answer a question.
 *
 * <p>The query goes through the same model as the corpus did — vectors from two
 * different models sit in different spaces and comparing them yields noise that looks
 * like a result.
 */
@Service
public class SearchService {

    private final EmbeddingModel embeddingModel;
    private final ChunkRepository chunks;
    private final ArticleRepository articles;
    private final String model;

    public SearchService(EmbeddingModel embeddingModel, ChunkRepository chunks,
                         ArticleRepository articles,
                         @Value("${spring.ai.ollama.embedding.model:bge-m3}") String model) {
        this.embeddingModel = embeddingModel;
        this.chunks = chunks;
        this.articles = articles;
        this.model = model;
    }

    @Transactional(readOnly = true)
    public SearchResult search(SearchQuery query) {
        long start = System.currentTimeMillis();

        List<Object[]> nearest = chunks.findNearest(
                literal(embeddingModel.embed(query.query())), query.minScore(), query.topK());

        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (Object[] row : nearest) {
            scores.put(((Number) row[0]).intValue(), ((Number) row[1]).doubleValue());
        }

        // The nearest-neighbour query returns ids; the rows come back through JPA so the
        // article behind each chunk is available without a second hand-written join.
        List<ChunkHit> hits = chunks.findAllById(scores.keySet()).stream()
                .map(chunk -> hit(chunk, scores.get(chunk.getId())))
                .sorted(Comparator.comparingDouble(ChunkHit::score).reversed())
                .toList();

        List<ReferencedArticle> referenced =
                query.expandRefs() ? referenced(hits) : List.of();

        return new SearchResult(query.query(), model, hits.size(), hits, referenced,
                System.currentTimeMillis() - start);
    }

    private ChunkHit hit(Chunk chunk, double score) {
        Article article = chunk.getArticle();
        return new ChunkHit(chunk.getId(), round(score), article.getId(), article.getDieuNo(),
                chunk.getKhoanRange(), article.getTitle(), article.getSourceLaw(),
                chunk.getChunkType(), List.of(chunk.getCrossRefs()), chunk.getContent());
    }

    /**
     * The articles the hits point at, minus the ones already returned.
     *
     * <p>References are resolved against the host statute. Inside the corpus a bare
     * "Điều 169" means the host code — the quoted statutes spell it out as "Điều 169
     * của Bộ luật Lao động" — so a hit on the Social Insurance Law's Điều 54 correctly
     * pulls in the Labour Code's retirement-age article rather than a Điều 169 of its
     * own, which does not exist here.
     */
    private List<ReferencedArticle> referenced(List<ChunkHit> hits) {
        Map<Integer, Set<Integer>> citedBy = new LinkedHashMap<>();
        Set<Integer> alreadyReturned = new LinkedHashSet<>();
        for (ChunkHit hit : hits) {
            alreadyReturned.add(hit.dieuNo());
        }
        for (ChunkHit hit : hits) {
            for (Integer ref : hit.crossRefs()) {
                if (!alreadyReturned.contains(ref)) {
                    citedBy.computeIfAbsent(ref, k -> new LinkedHashSet<>()).add(hit.dieuNo());
                }
            }
        }
        if (citedBy.isEmpty()) {
            return List.of();
        }

        Article any = articles.findById(hits.get(0).articleId()).orElseThrow();
        Integer documentId = any.getDocument().getId();
        String hostLaw = any.getDocument().getCode();

        List<ReferencedArticle> result = new ArrayList<>();
        for (Article article : articles.findByDocumentIdAndSourceLawAndDieuNoIn(
                documentId, hostLaw, citedBy.keySet())) {
            result.add(new ReferencedArticle(article.getId(), article.getDieuNo(),
                    article.getTitle(), article.getSourceLaw(),
                    List.copyOf(citedBy.get(article.getDieuNo())), article.getFullText()));
        }
        result.sort(Comparator.comparingInt(ReferencedArticle::dieuNo));
        return result;
    }

    /** pgvector's own text form, {@code [0.1,0.2,…]}, which the query casts back. */
    private String literal(float[] vector) {
        StringJoiner joined = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joined.add(Float.toString(value));
        }
        return joined.toString();
    }

    private double round(double score) {
        return Math.round(score * 10000.0) / 10000.0;
    }
}
