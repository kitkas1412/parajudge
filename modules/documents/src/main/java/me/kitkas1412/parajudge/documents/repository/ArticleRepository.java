package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Integer> {

    /**
     * Looks up articles by number within one statute.
     *
     * <p>{@code sourceLaw} is not optional: Điều 54 exists both in the Labour Code and
     * in the Social Insurance Law that Điều 219 quotes, so a number alone does not
     * identify an article.
     */
    List<Article> findByDocumentIdAndSourceLawAndDieuNoIn(
            Integer documentId, String sourceLaw, Collection<Integer> dieuNos);
}
