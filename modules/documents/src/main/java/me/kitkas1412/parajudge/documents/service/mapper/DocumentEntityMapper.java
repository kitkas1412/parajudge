package me.kitkas1412.parajudge.documents.service.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.entity.Section;
import me.kitkas1412.parajudge.documents.service.parser.model.Amendment;
import me.kitkas1412.parajudge.documents.service.parser.model.AmendmentItem;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedArticle;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedChapter;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedSection;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a {@link ParsedDocument} into the persistent graph
 * {@code Document → Chapter → Section → Article}.
 *
 * <p>The parse tree goes one level deeper than the schema does: it also carries
 * Khoản and Điểm, which have no table. They survive inside
 * {@code articles.full_text}, so nothing is lost — {@code chunks} is where that
 * detail is meant to be split up again, and that needs a chunking strategy and an
 * embedding model, so this mapper leaves it alone.
 *
 * <p>Saving the returned {@code Document} saves the whole graph: chapters and
 * articles cascade from it, sections cascade from their chapter.
 */
@Component
public class DocumentEntityMapper {

    private final PreambleParser preambleParser = new PreambleParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Document toEntity(ParsedDocument parsed) {
        List<LawReference> laws = preambleParser.parse(parsed.preamble());
        LawReference self = laws.isEmpty() ? null : laws.get(0);
        List<LawReference> amendedBy = laws.size() > 1 ? laws.subList(1, laws.size()) : List.of();

        Document document = new Document(
                code(parsed, self),
                title(parsed, self),
                self == null ? null : self.issuedDate(),
                self == null ? null : self.effectiveDate(),
                amendedBy(amendedBy));

        for (ParsedChapter parsedChapter : parsed.chapters()) {
            Chapter chapter = new Chapter(document, parsedChapter.no(), parsedChapter.title());

            for (ParsedSection parsedSection : parsedChapter.sections()) {
                Section section = new Section(chapter, parsedSection.no(), parsedSection.title());
                for (ParsedArticle article : parsedSection.articles()) {
                    toEntity(document, chapter, section, article);
                }
            }
            for (ParsedArticle article : parsedChapter.articles()) {
                toEntity(document, chapter, null, article);
            }
        }
        return document;
    }

    private void toEntity(Document document, Chapter chapter, Section section, ParsedArticle parsed) {
        new Article(document, chapter, section, parsed.no(), title(parsed), parsed.fullText(),
                null, document.getCode());
        amendedStatutes(document, chapter, parsed);
    }

    /**
     * Điều 219 quotes whole articles of other statutes. They are stored as articles of
     * their own, told apart by {@code source_law} — which is what the column is for.
     * Amendments that rewrite only a clause ("sửa đổi khoản 1 Điều 73") name no article
     * to stand on its own and stay inside Điều 219's own text.
     */
    private void amendedStatutes(Document document, Chapter chapter, ParsedArticle parsed) {
        for (Amendment amendment : parsed.amendments()) {
            for (AmendmentItem item : amendment.items()) {
                if (item.targetArticleNo() == null || item.quotedText().isEmpty()) {
                    continue;
                }
                new Article(document, chapter, null, item.targetArticleNo(),
                        item.targetArticleTitle() == null
                                ? "Điều " + item.targetArticleNo()
                                : item.targetArticleTitle(),
                        String.join("\n", item.quotedText()),
                        null, amendment.targetLaw());
            }
        }
    }

    private String code(ParsedDocument parsed, LawReference self) {
        if (parsed.metadata().code() != null) {
            return parsed.metadata().code();
        }
        return self == null ? null : self.code();
    }

    /** The preamble spells the name properly; the cover page shouts it in capitals. */
    private String title(ParsedDocument parsed, LawReference self) {
        if (self != null && self.title() != null && !self.title().isBlank()) {
            return self.title();
        }
        return parsed.metadata().title();
    }

    private String title(ParsedArticle parsed) {
        return parsed.title() == null || parsed.title().isBlank()
                ? "Điều " + parsed.no()
                : parsed.title();
    }

    private JsonNode amendedBy(List<LawReference> laws) {
        if (laws.isEmpty()) {
            return null;
        }
        ArrayNode array = objectMapper.createArrayNode();
        for (LawReference law : laws) {
            ObjectNode node = array.addObject();
            node.put("code", law.code());
            node.put("title", law.title());
            if (law.issuedDate() != null) {
                node.put("issued_date", law.issuedDate().toString());
            }
            if (law.effectiveDate() != null) {
                node.put("effective_date", law.effectiveDate().toString());
            }
        }
        return array;
    }
}
