package me.kitkas1412.parajudge.ingestion;

import me.kitkas1412.parajudge.documents.parser.model.DocumentMetadata;
import me.kitkas1412.parajudge.documents.parser.model.ParsedArticle;
import me.kitkas1412.parajudge.documents.parser.model.ParsedChapter;
import me.kitkas1412.parajudge.documents.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.parser.model.ParsedSection;

import java.util.List;

/**
 * The tree with the body text taken out — the shape of a parse at a glance.
 *
 * <p>The full tree of the 86-page code is ~600 KB of JSON, which is not something
 * you can eyeball in a browser. This keeps the numbering and the counts, which is
 * what tells you whether the parse went right.
 */
public record DocumentOutline(
        DocumentMetadata metadata,
        List<Chapter> chapters) {

    public record Chapter(String no, String title, List<Section> sections, List<Article> articles) {
    }

    public record Section(String no, String title, List<Article> articles) {
    }

    public record Article(int no, String title, int page, int clauses, int points, int amendments) {
    }

    public static DocumentOutline of(ParsedDocument document) {
        return new DocumentOutline(document.metadata(),
                document.chapters().stream().map(DocumentOutline::chapter).toList());
    }

    private static Chapter chapter(ParsedChapter chapter) {
        return new Chapter(chapter.no(), chapter.title(),
                chapter.sections().stream().map(DocumentOutline::section).toList(),
                chapter.articles().stream().map(DocumentOutline::article).toList());
    }

    private static Section section(ParsedSection section) {
        return new Section(section.no(), section.title(),
                section.articles().stream().map(DocumentOutline::article).toList());
    }

    private static Article article(ParsedArticle article) {
        int points = article.clauses().stream().mapToInt(c -> c.points().size()).sum();
        return new Article(article.no(), article.title(), article.page(),
                article.clauses().size(), points, article.amendments().size());
    }
}
