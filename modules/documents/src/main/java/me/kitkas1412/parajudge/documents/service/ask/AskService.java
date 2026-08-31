package me.kitkas1412.parajudge.documents.service.ask;

import me.kitkas1412.parajudge.documents.service.search.ChunkHit;
import me.kitkas1412.parajudge.documents.service.search.ReferencedArticle;
import me.kitkas1412.parajudge.documents.service.search.SearchResult;
import me.kitkas1412.parajudge.documents.service.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Answers a question from the corpus — retrieval-augmented generation, one pass.
 *
 * <p>Retrieve once, put what came back in front of the model, generate once. The model
 * decides nothing about what to look up; that is what makes this the plain form rather
 * than an agentic one, and it is also what makes it reproducible, which matters when
 * someone acts on the answer.
 *
 * <p>Three things keep it from inventing law:
 * <ul>
 *   <li>nothing retrieved above the floor means no model call at all — the question is
 *       reported unanswerable instead of answered from whatever came closest;
 *   <li>the model returns chunk <em>ids</em>, and every citation is then built from the
 *       database rows behind them, so a citation cannot name an article that isn't there;
 *   <li>the model may set {@code answerable = false}, which is the honest outcome when
 *       the retrieved text is about the right subject but does not settle the question.
 * </ul>
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý tra cứu văn bản pháp luật Việt Nam.

            QUY TẮC BẮT BUỘC:

            1. Chỉ trả lời dựa trên các trích đoạn được cung cấp. Tuyệt đối không dùng
               kiến thức sẵn có của bạn về pháp luật Việt Nam — luật sửa đổi liên tục,
               và bản trong trích đoạn mới là bản đang có hiệu lực.

            2. Nếu các trích đoạn không đủ để trả lời dứt khoát, đặt answerable = false
               và nói rõ còn thiếu thông tin gì. Không suy đoán, không lấp chỗ trống.

            3. Mỗi khẳng định phải dựa trên một trích đoạn cụ thể. Liệt kê id của đúng
               những trích đoạn bạn thực sự dùng vào citedChunkIds. Không liệt kê id
               không được cung cấp.

            4. Trả lời bằng tiếng Việt, ngắn gọn, và dẫn số Điều/khoản ngay trong câu văn
               (ví dụ: "theo khoản 1 Điều 113"). Nếu các trích đoạn thuộc nhiều luật khác
               nhau, nói rõ điều khoản đó thuộc luật nào.

            5. Nếu trích đoạn mâu thuẫn nhau hoặc điều khoản đã bị sửa đổi, nêu ra thay vì
               chọn bừa một bên.
            """;

    private final SearchService search;
    private final ChatClient chat;

    public AskService(SearchService search, ChatClient.Builder chatClientBuilder) {
        this.search = search;
        this.chat = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public Answer ask(AskQuery query) {
        long start = System.currentTimeMillis();

        SearchResult retrieved = search.search(query.toSearchQuery());
        if (retrieved.hits().isEmpty()) {
            // No model call: there is nothing to ground an answer in, and asking anyway
            // is exactly how a legal assistant ends up inventing a statute.
            return Answer.notFound(query.question(),
                    "Không tìm thấy điều khoản nào liên quan trong văn bản đã nạp.",
                    System.currentTimeMillis() - start);
        }

        ResponseEntity<ChatResponse, GroundedAnswer> response;
        try {
            response = chat.prompt()
                    .user(userPrompt(query.question(), retrieved))
                    .call()
                    .responseEntity(GroundedAnswer.class);
        } catch (RuntimeException e) {
            // Not folded into answered=false: that would tell the caller the corpus is
            // silent, when what actually happened is that nobody asked the model.
            throw new AnswerGenerationException(e);
        }

        GroundedAnswer generated = response.entity();
        return new Answer(query.question(), generated.answerable(), generated.answer(),
                citations(generated, retrieved.hits()), model(response.response()),
                retrieved.hits(), retrieved.referenced(),
                System.currentTimeMillis() - start);
    }

    /**
     * Which model actually answered, read off the response rather than off configuration.
     * The provider is a property away from changing, and a reported model that does not
     * match the one that produced the text makes every stored answer unauditable.
     */
    private String model(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        String model = response.getMetadata().getModel();
        return model == null || model.isBlank() ? null : model;
    }

    private String userPrompt(String question, SearchResult retrieved) {
        StringBuilder prompt = new StringBuilder("CÂU HỎI: ").append(question)
                .append("\n\nCÁC TRÍCH ĐOẠN:\n");
        for (ChunkHit hit : retrieved.hits()) {
            prompt.append("\n[#").append(hit.chunkId()).append("] ")
                    .append(hit.citation()).append(" — ").append(hit.articleTitle())
                    .append(" (").append(hit.sourceLaw()).append(")\n")
                    .append(hit.content()).append('\n');
        }
        if (!retrieved.referenced().isEmpty()) {
            prompt.append("\nCÁC ĐIỀU ĐƯỢC DẪN CHIẾU TỚI (tham khảo, không có id để trích dẫn):\n");
            for (ReferencedArticle article : retrieved.referenced()) {
                prompt.append("\nĐiều ").append(article.dieuNo()).append(". ")
                        .append(article.title()).append(" (").append(article.sourceLaw()).append(")\n")
                        .append(article.fullText()).append('\n');
            }
        }
        return prompt.toString();
    }

    /**
     * Turns the model's chosen ids into citations built from the retrieved rows.
     *
     * <p>An id the model made up is dropped rather than passed on — it would otherwise
     * arrive as a citation with no source behind it, which is the one thing this whole
     * layer exists to prevent.
     */
    private List<Citation> citations(GroundedAnswer generated, List<ChunkHit> hits) {
        if (generated.citedChunkIds() == null) {
            return List.of();
        }
        Map<Integer, ChunkHit> byId = hits.stream()
                .collect(Collectors.toMap(ChunkHit::chunkId, Function.identity(), (a, b) -> a));

        List<Citation> citations = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (Integer id : generated.citedChunkIds()) {
            ChunkHit hit = byId.get(id);
            if (hit == null) {
                log.warn("Model trich dan chunk #{} khong nam trong ket qua tim kiem — bo qua", id);
                continue;
            }
            if (seen.add(id)) {
                citations.add(new Citation(hit.chunkId(), hit.dieuNo(), hit.khoanRange(),
                        hit.articleTitle(), hit.sourceLaw(), hit.citation()));
            }
        }
        return citations;
    }
}
