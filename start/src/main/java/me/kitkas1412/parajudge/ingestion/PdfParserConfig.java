package me.kitkas1412.parajudge.ingestion;

import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The parser is deliberately framework-free — it is driven from a CLI and from
 * tests as well — so it is published as a bean here rather than annotated.
 */
@Configuration
public class PdfParserConfig {

    @Bean
    public PdfIngestionService pdfIngestionService() {
        return new PdfIngestionService();
    }
}
