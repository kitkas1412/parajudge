package me.kitkas1412.parajudge.ingestion;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only — the parser needs no database, so neither does this. */
@WebMvcTest(PdfParserController.class)
@Import(PdfParserConfig.class)
class PdfParserControllerTest {

    private static final String SAMPLE = "boluatlaodong-trang-1.pdf";

    @Autowired
    private MockMvc mockMvc;

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
}
