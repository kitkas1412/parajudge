package me.kitkas1412.parajudge.documents.controller;

import me.kitkas1412.parajudge.documents.service.embedding.EmbeddingResult;
import me.kitkas1412.parajudge.documents.service.embedding.EmbeddingService;
import me.kitkas1412.parajudge.documents.service.ingestion.DocumentIngestionService;
import me.kitkas1412.parajudge.documents.service.ingestion.DuplicateDocumentException;
import me.kitkas1412.parajudge.documents.service.ingestion.IngestionResult;
import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only — the parser needs no database, so neither does this. */
@WebMvcTest(PdfParserController.class)
@Import(PdfIngestionService.class)  // @WebMvcTest scans web components only
class PdfParserControllerTest {

    private static final String SAMPLE = "boluatlaodong-trang-1.pdf";

    @Autowired
    private MockMvc mockMvc;

    /** Ingestion needs a database; what this test covers is the HTTP contract around it. */
    @MockitoBean
    private DocumentIngestionService ingestionService;

    /** Embedding needs a model server, for the same reason. */
    @MockitoBean
    private EmbeddingService embeddingService;

    private static EmbeddingResult embedded(int count) {
        return new EmbeddingResult("bge-m3", 1024, count, 0, 6000, 5900, 0.983, 1200);
    }

    private byte[] samplePdf() throws Exception {
        try (InputStream in = new ClassPathResource("pdf/" + SAMPLE).getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void listsBundledSamples() throws Exception {
        mockMvc.perform(get("/api/parser/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(Matchers.hasItem(SAMPLE)));
    }

    @Test
    void returnsTheOutlineByDefault() throws Exception {
        mockMvc.perform(get("/api/parser/samples/{name}", SAMPLE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.metadata.code").value("45/2019/QH14"))
                .andExpect(jsonPath("$.metadata.articleCount").value(24))
                .andExpect(jsonPath("$.chapters.length()").value(3))
                .andExpect(jsonPath("$.chapters[2].sections[0].title").value("GIAO KẾT HỢP ĐỒNG LAO ĐỘNG"))
                // The outline carries counts, not body text.
                .andExpect(jsonPath("$.chapters[0].articles[4].clauses").value(2))
                .andExpect(jsonPath("$.chapters[0].articles[4].points").value(10));
    }

    @Test
    void returnsTheWholeTreeOnRequest() throws Exception {
        mockMvc.perform(get("/api/parser/samples/{name}", SAMPLE).param("view", "full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapters[0].articles[4].clauses[0].points[4].no").value("đ"))
                .andExpect(jsonPath("$.footnotes[0].marker").value("1"));
    }

    @Test
    void parsesAnUpload() throws Exception {
        MockMultipartFile upload =
                new MockMultipartFile("file", SAMPLE, MediaType.APPLICATION_PDF_VALUE, samplePdf());

        mockMvc.perform(multipart("/api/parser/parse").file(upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.sourceFile").value(SAMPLE))
                .andExpect(jsonPath("$.metadata.articleCount").value(24));
    }

    @Test
    void rejectsSomethingThatIsNotAPdf() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "Điều 1. Không phải PDF".getBytes());

        mockMvc.perform(multipart("/api/parser/parse").file(upload))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void refusesToWalkOutOfTheSampleDirectory() throws Exception {
        mockMvc.perform(get("/api/parser/samples/{name}", "..%2f..%2fapplication.yaml"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void reportsAnUnknownSample() throws Exception {
        mockMvc.perform(get("/api/parser/samples/{name}", "khong-ton-tai.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnUnknownView() throws Exception {
        mockMvc.perform(get("/api/parser/samples/{name}", SAMPLE).param("view", "xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestsASampleAndReportsWhatWasWritten() throws Exception {
        when(ingestionService.ingest(any(), eq(false))).thenReturn(new IngestionResult(
                7, "45/2019/QH14", "Bộ luật Lao động", 3, 1, 24, 31, 10, List.of(), false, null, null));

        when(embeddingService.embed(false)).thenReturn(embedded(31));

        mockMvc.perform(post("/api/parser/ingest/samples/{name}", SAMPLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(7))
                .andExpect(jsonPath("$.code").value("45/2019/QH14"))
                .andExpect(jsonPath("$.articles").value(24))
                .andExpect(jsonPath("$.chunks").value(31))
                .andExpect(jsonPath("$.replacedExisting").value(false));
    }

    @Test
    void ingestsAnUpload() throws Exception {
        when(ingestionService.ingest(any(), eq(false))).thenReturn(new IngestionResult(
                1, "45/2019/QH14", "Bộ luật Lao động", 3, 1, 24, 31, 10, List.of(), false, null, null));
        MockMultipartFile upload =
                new MockMultipartFile("file", SAMPLE, MediaType.APPLICATION_PDF_VALUE, samplePdf());

        when(embeddingService.embed(false)).thenReturn(embedded(31));

        mockMvc.perform(multipart("/api/parser/ingest").file(upload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(1));
    }

    @Test
    void embedsAsPartOfTheIngestByDefault() throws Exception {
        when(ingestionService.ingest(any(), eq(false))).thenReturn(new IngestionResult(
                7, "45/2019/QH14", "Bộ luật Lao động", 3, 1, 24, 31, 10, List.of(), false, null, null));
        when(embeddingService.embed(false)).thenReturn(embedded(31));

        mockMvc.perform(post("/api/parser/ingest/samples/{name}", SAMPLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunks").value(31))
                .andExpect(jsonPath("$.embedding.embedded").value(31))
                .andExpect(jsonPath("$.embedding.model").value("bge-m3"))
                .andExpect(jsonPath("$.embedding.dimensions").value(1024))
                .andExpect(jsonPath("$.embeddingError").doesNotExist());
    }

    @Test
    void skipsEmbeddingWhenAsked() throws Exception {
        when(ingestionService.ingest(any(), eq(false))).thenReturn(new IngestionResult(
                7, "45/2019/QH14", "Bộ luật Lao động", 3, 1, 24, 31, 10, List.of(), false, null, null));

        mockMvc.perform(post("/api/parser/ingest/samples/{name}", SAMPLE).param("embed", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunks").value(31))
                .andExpect(jsonPath("$.embedding").doesNotExist());

        verifyNoInteractions(embeddingService);
    }

    @Test
    void stillReportsTheIngestWhenTheModelServerIsDown() throws Exception {
        when(ingestionService.ingest(any(), eq(false))).thenReturn(new IngestionResult(
                7, "45/2019/QH14", "Bộ luật Lao động", 3, 1, 24, 31, 10, List.of(), false, null, null));
        when(embeddingService.embed(false))
                .thenThrow(new RuntimeException("Connection refused: localhost/127.0.0.1:11434"));

        // The statute is committed by the time embedding runs, so this is a 201 with a
        // note attached, not a 5xx that would claim the ingest failed.
        mockMvc.perform(post("/api/parser/ingest/samples/{name}", SAMPLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(7))
                .andExpect(jsonPath("$.chunks").value(31))
                .andExpect(jsonPath("$.embedding").doesNotExist())
                .andExpect(jsonPath("$.embeddingError").value(Matchers.containsString("11434")));
    }

    @Test
    void refusesToIngestTheSameStatuteTwice() throws Exception {
        when(ingestionService.ingest(any(), eq(false)))
                .thenThrow(new DuplicateDocumentException("45/2019/QH14"));

        mockMvc.perform(post("/api/parser/ingest/samples/{name}", SAMPLE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("45/2019/QH14"))
                .andExpect(jsonPath("$.hint").value("Gọi lại với ?replace=true để ghi đè"));
    }

    @Test
    void answers200WhenItReplacedInsteadOfCreated() throws Exception {
        when(ingestionService.ingest(any(), eq(true))).thenReturn(new IngestionResult(
                8, "45/2019/QH14", "Bộ luật Lao động", 3, 1, 24, 31, 10, List.of(), true, null, null));

        when(embeddingService.embed(false)).thenReturn(embedded(31));

        mockMvc.perform(post("/api/parser/ingest/samples/{name}", SAMPLE).param("replace", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replacedExisting").value(true));
    }

    @Test
    void doesNotIngestSomethingThatIsNotAPdf() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "Điều 1. Không phải PDF".getBytes());

        mockMvc.perform(multipart("/api/parser/ingest").file(upload))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(ingestionService);
    }
}
