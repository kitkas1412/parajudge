package me.kitkas1412.parajudge.documents.controller;

import me.kitkas1412.parajudge.documents.service.ask.Answer;
import me.kitkas1412.parajudge.documents.service.ask.AnswerGenerationException;
import me.kitkas1412.parajudge.documents.service.ask.AskQuery;
import me.kitkas1412.parajudge.documents.service.ask.AskService;
import me.kitkas1412.parajudge.documents.service.search.SearchQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Question answering over the corpus. */
@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    /**
     * {@code POST /api/ask}
     *
     * <pre>{@code {"question": "Nghỉ hằng năm được bao nhiêu ngày?"}}</pre>
     *
     * <p>Always 200 — a question the corpus cannot answer is a valid outcome reported as
     * {@code answered: false}, not an error.
     */
    @PostMapping
    public Answer ask(@RequestBody AskRequest request) {
        return askService.ask(request.toQuery());
    }

    @ExceptionHandler(AnswerGenerationException.class)
    public ResponseEntity<Map<String, String>> onModelUnavailable(AnswerGenerationException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", e.getMessage(),
                "hint", "Kiểm tra model sinh câu trả lời đang cấu hình ở spring.ai.model.chat "
                        + "(Ollama có chạy không? API key đã đặt chưa?). "
                        + "Việc tìm kiếm vẫn dùng được qua POST /api/search."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadQuestion(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    /** Body of {@code POST /api/ask}; every field but {@code question} has a default. */
    public record AskRequest(String question, Integer topK, Double minScore, Boolean expandRefs) {

        AskQuery toQuery() {
            return new AskQuery(
                    question,
                    topK == null ? AskQuery.DEFAULT_TOP_K : topK,
                    minScore == null ? SearchQuery.DEFAULT_MIN_SCORE : minScore,
                    expandRefs != null && expandRefs);
        }
    }
}
