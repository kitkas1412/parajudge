package me.kitkas1412.parajudge.documents.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import me.kitkas1412.parajudge.documents.parser.LegalDocumentParser;
import me.kitkas1412.parajudge.documents.parser.model.DocumentMetadata;
import me.kitkas1412.parajudge.documents.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.parser.pdf.PageText;
import me.kitkas1412.parajudge.documents.parser.pdf.PdfPageExtractor;
import me.kitkas1412.parajudge.documents.parser.pdf.ScannedPageFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a Vietnamese legal PDF and produces the hierarchical
 * Chương / Mục / Điều / Khoản / Điểm tree, optionally as JSON on disk.
 *
 * <p>Three stages: extract page by page with PDFBox keeping font size and
 * indentation, drop the scanned pages appended at the end, then parse the
 * structure.
 */
public class PdfIngestionService {

    private final PdfPageExtractor extractor = new PdfPageExtractor();
    private final ScannedPageFilter scannedPageFilter = new ScannedPageFilter();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public ParsedDocument parse(Path pdf) throws IOException {
        return parse(extractor.extract(pdf), pdf.getFileName().toString());
    }

    /** For content that never reaches the filesystem, such as an upload. */
    public ParsedDocument parse(byte[] pdf, String sourceName) throws IOException {
        return parse(extractor.extract(pdf), sourceName);
    }

    private ParsedDocument parse(List<PageText> allPages, String sourceName) {
        ScannedPageFilter.Result filtered = scannedPageFilter.removeTrailingScans(allPages);

        DocumentMetadata partial = new DocumentMetadata(
                sourceName, null, null,
                allPages.size(), filtered.pages().size(), filtered.droppedPages(), 0, 0);

        return new LegalDocumentParser().parse(partial, filtered.pages());
    }

    public ParsedDocument parseToJson(Path pdf, Path jsonOut) throws IOException {
        ParsedDocument document = parse(pdf);
        if (jsonOut.getParent() != null) {
            Files.createDirectories(jsonOut.getParent());
        }
        objectMapper.writeValue(jsonOut.toFile(), document);
        return document;
    }

    public String toJson(ParsedDocument document) throws IOException {
        return objectMapper.writeValueAsString(document);
    }
}
