package me.kitkas1412.parajudge.documents.controller;

import me.kitkas1412.parajudge.documents.service.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.service.ingestion.PdfIngestionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Exercises the legal-PDF parser over HTTP: parse one of the PDFs bundled in the
 * classpath, or upload one.
 *
 * <p>Every endpoint answers with the outline by default — the full tree of the
 * 86-page code is ~600 KB — and with the whole tree on {@code ?view=full}.
 */
@RestController
@RequestMapping("/api/parser")
public class PdfParserController {

    private static final String SAMPLE_DIR = "pdf/";

    /** A sample name addresses a classpath resource, so it must not be able to escape the directory. */
    private static final Pattern SAFE_SAMPLE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}\\.pdf");

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final PdfIngestionService ingestionService;

    public PdfParserController(PdfIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** {@code GET /api/parser/samples} — the PDFs bundled with the application. */
    @GetMapping("/samples")
    public List<String> samples() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + SAMPLE_DIR + "*.pdf");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** {@code GET /api/parser/samples/boluatlaodong-trang-1.pdf?view=outline} */
    @GetMapping("/samples/{name}")
    public Object parseSample(@PathVariable String name,
                              @RequestParam(defaultValue = "outline") String view) throws IOException {
        if (!SAFE_SAMPLE_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ: " + name);
        }
        ClassPathResource resource = new ClassPathResource(SAMPLE_DIR + name);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có file mẫu: " + name);
        }
        byte[] content;
        try (InputStream in = resource.getInputStream()) {
            content = in.readAllBytes();
        }
        return render(parse(content, name), view);
    }

    /** {@code POST /api/parser/parse} with a {@code file} part. */
    @PostMapping("/parse")
    public Object parseUpload(@RequestParam("file") MultipartFile file,
                              @RequestParam(defaultValue = "outline") String view) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File rỗng");
        }
        byte[] content = file.getBytes();
        if (!looksLikePdf(content)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File không phải PDF");
        }
        String name = file.getOriginalFilename() == null ? "upload.pdf" : file.getOriginalFilename();
        return render(parse(content, name), view);
    }

    private ParsedDocument parse(byte[] content, String name) throws IOException {
        try {
            return ingestionService.parse(content, name);
        } catch (IOException e) {
            // PDFBox reports a corrupt or encrypted file as an IOException; that is the
            // caller's problem, not a server fault.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không đọc được PDF: " + e.getMessage(), e);
        }
    }

    private Object render(ParsedDocument document, String view) {
        return switch (view) {
            case "full" -> document;
            case "outline" -> DocumentOutline.of(document);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "view phải là 'outline' hoặc 'full', nhận được: " + view);
        };
    }

    private boolean looksLikePdf(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            return false;
        }
        return Arrays.equals(content, 0, PDF_MAGIC.length, PDF_MAGIC, 0, PDF_MAGIC.length);
    }
}
