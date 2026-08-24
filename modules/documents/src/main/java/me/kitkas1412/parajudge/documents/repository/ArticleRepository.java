package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Integer> {
}
