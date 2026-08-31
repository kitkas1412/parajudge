package me.kitkas1412.parajudge.documents.controller;

import me.kitkas1412.parajudge.documents.service.search.SearchQuery;
import me.kitkas1412.parajudge.documents.service.search.SearchResult;
import me.kitkas1412.parajudge.documents.service.search.SearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Retrieval over the embedded corpus. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * {@code POST /api/search} with a JSON body — the full form.
     *
     * <pre>{@code {"query": "nghỉ hằng năm bao nhiêu ngày", "topK": 5,
     *              "minScore": 0.5, "expandRefs": true}}</pre>
     */
    @PostMapping
    public SearchResult search(@RequestBody SearchRequest request) {
        return searchService.search(request.toQuery());
    }

    /** {@code GET /api/search?q=…} — the same thing, for a quick look from a browser. */
    @GetMapping
    public SearchResult search(
            @RequestParam("q") String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.5") double minScore,
            @RequestParam(defaultValue = "false") boolean expandRefs) {
        return searchService.search(new SearchQuery(q, topK, minScore, expandRefs));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadQuery(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    /** Body of {@code POST /api/search}; every field but {@code query} has a default. */
    public record SearchRequest(String query, Integer topK, Double minScore, Boolean expandRefs) {

        SearchQuery toQuery() {
            return new SearchQuery(
                    query,
                    topK == null ? SearchQuery.DEFAULT_TOP_K : topK,
                    minScore == null ? SearchQuery.DEFAULT_MIN_SCORE : minScore,
                    expandRefs != null && expandRefs);
        }
    }
}
