package me.kitkas1412.parajudge.documents.service.ingestion;

import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.repository.ArticleRepository;
import me.kitkas1412.parajudge.documents.repository.DocumentRepository;
import me.kitkas1412.parajudge.documents.service.mapper.DocumentEntityMapper;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Writes a parsed statute into the database.
 *
 * <p>Takes an already-parsed document rather than the PDF, so the ~500 ms of PDFBox
 * work happens before the transaction opens instead of holding a connection through it.
 *
 * <p>Ingesting the same PDF twice would otherwise leave two copies of the statute
 * behind, with nothing in the schema to prevent it, so a code that is already present
 * is refused unless the caller asks for a replacement.
 */
@Service
public class DocumentIngestionService {

    private final DocumentRepository documents;
    private final ArticleRepository articles;
    private final DocumentEntityMapper mapper;

    public DocumentIngestionService(DocumentRepository documents, ArticleRepository articles,
                                    DocumentEntityMapper mapper) {
        this.documents = documents;
        this.articles = articles;
        this.mapper = mapper;
    }

    /**
     * @param replace delete the statute already stored under this code and write this one
     * @throws DuplicateDocumentException if the code is present and {@code replace} is false
     */
    @Transactional
    public IngestionResult ingest(ParsedDocument parsed, boolean replace) {
        Document document = mapper.toEntity(parsed);

        boolean replacedExisting = false;
        Optional<Document> existing = document.getCode() == null
                ? Optional.empty()
                : documents.findByCode(document.getCode());
        if (existing.isPresent()) {
            if (!replace) {
                throw new DuplicateDocumentException(document.getCode());
            }
            delete(existing.get());
            replacedExisting = true;
        }
        return IngestionResult.of(documents.save(document), parsed, replacedExisting);
    }

    /**
     * Articles carry the foreign keys to chapters and sections, so they have to go
     * first; cascading from the document alone leaves Hibernate free to delete the
     * chapters while articles still point at them.
     */
    private void delete(Document document) {
        articles.deleteAll(document.getArticles());
        articles.flush();
        documents.delete(document);
        documents.flush();
    }
}
