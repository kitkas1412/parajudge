package me.kitkas1412.parajudge.documents.service.parser;

import me.kitkas1412.parajudge.documents.service.parser.model.DocumentMetadata;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedChapter;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.service.parser.model.ParsedSection;
import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;

import java.nio.file.Path;

/** {@code java … LegalPdfParserCli <input.pdf> [output.json]} */
public final class LegalPdfParserCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: LegalPdfParserCli <input.pdf> [output.json]");
            System.exit(2);
        }
        Path pdf = Path.of(args[0]);
        Path out = Path.of(args.length > 1 ? args[1]
                : pdf.getFileName().toString().replaceAll("\\.pdf$", "") + ".json");

        ParsedDocument document = new PdfIngestionService().parseToJson(pdf, out);
        DocumentMetadata metadata = document.metadata();

        System.out.printf("%s -> %s%n", pdf, out);
        System.out.printf("  %s (%s)%n", metadata.title(), metadata.code());
        System.out.printf("  pages: %d parsed, %d dropped as scans %s%n",
                metadata.parsedPages(),
                metadata.totalPages() - metadata.parsedPages(),
                metadata.droppedScanPages());
        System.out.printf("  %d chapters, %d articles, %d footnotes%n",
                metadata.chapterCount(), metadata.articleCount(), document.footnotes().size());
        for (ParsedChapter chapter : document.chapters()) {
            System.out.printf("  Chương %s. %s — %d điều trực thuộc, %d mục%n",
                    chapter.no(), chapter.title(), chapter.articles().size(), chapter.sections().size());
            for (ParsedSection section : chapter.sections()) {
                System.out.printf("      Mục %s. %s — %d điều%n",
                        section.no(), section.title(), section.articles().size());
            }
        }
    }

    private LegalPdfParserCli() {
    }
}
