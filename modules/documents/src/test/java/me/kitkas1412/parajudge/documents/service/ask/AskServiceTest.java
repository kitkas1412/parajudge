package me.kitkas1412.parajudge.documents.service.ask;

import me.kitkas1412.parajudge.documents.service.search.ChunkHit;
import me.kitkas1412.parajudge.documents.service.search.SearchQuery;
import me.kitkas1412.parajudge.documents.service.search.SearchResult;
import me.kitkas1412.parajudge.documents.service.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The grounding rules, with a stand-in for Claude.
 *
 * <p>{@link ChatModel} is mocked rather than {@link ChatClient}, so the real prompt
 * assembly and the real structured-output parsing both run — only the network call is
 * replaced.
 */
class AskServiceTest {

    private static final String HOST_LAW = "45/2019/QH14";

    private final SearchService search = mock(SearchService.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final AtomicReference<Prompt> sent = new AtomicReference<>();

    private AskService service() {
        // ChatClient reads the model's defaults when assembling a request; a bare mock
        // returns null there and never reaches the call.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        return new AskService(search, ChatClient.builder(chatModel));
    }

    private void modelAnswers(String json) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        });
    }

    private ChunkHit hit(int chunkId, int dieuNo, String khoanRange, String title, double score) {
        return new ChunkHit(chunkId, score, dieuNo * 10, dieuNo, khoanRange, title, HOST_LAW,
                "khoan_group", List.of(), "Bộ luật Lao động — Điều " + dieuNo + "\nnội dung");
    }

    private void retrieves(ChunkHit... hits) {
        when(search.search(any())).thenReturn(new SearchResult(
                "q", "bge-m3", hits.length, List.of(hits), List.of(), 10));
    }

    @Test
    void answersFromWhatWasRetrievedAndCitesIt() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.77),
                  hit(12, 114, null, "Ngày nghỉ tăng thêm", 0.71));
        modelAnswers("""
                {"answerable": true,
                 "answer": "12 ngày làm việc theo khoản 1 Điều 113.",
                 "citedChunkIds": [11]}""");

        Answer answer = service().ask(AskQuery.of("nghỉ hằng năm mấy ngày?"));

        assertThat(answer.answered()).isTrue();
        assertThat(answer.answer()).contains("Điều 113");
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().get(0).reference()).isEqualTo("Điều 113 khoản 1-5");
        assertThat(answer.citations().get(0).articleTitle()).isEqualTo("Nghỉ hằng năm");
        assertThat(answer.citations().get(0).sourceLaw()).isEqualTo(HOST_LAW);
        // Both chunks come back so a reader can check the answer against its sources.
        assertThat(answer.retrieved()).hasSize(2);
    }

    @Test
    void reportsTheModelThatActuallyAnswered() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.77));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(
                        "{\"answerable\": true, \"answer\": \"…\", \"citedChunkIds\": [11]}"))),
                org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
                        .model("qwen3:8b").build()));

        // Read off the response, not off configuration: the provider is one property
        // away from changing, and a mismatch makes every stored answer unauditable.
        assertThat(new AskService(search, ChatClient.builder(chatModel))
                .ask(AskQuery.of("nghỉ phép")).model()).isEqualTo("qwen3:8b");
    }

    @Test
    void doesNotCallTheModelWhenNothingWasRetrieved() {
        retrieves();

        Answer answer = service().ask(AskQuery.of("công thức nấu phở"));

        assertThat(answer.answered()).isFalse();
        assertThat(answer.answer()).contains("Không tìm thấy");
        assertThat(answer.citations()).isEmpty();
        // Asking anyway is how a legal assistant ends up inventing a statute.
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void letsTheModelDeclineWhenTheTextDoesNotSettleTheQuestion() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.62));
        modelAnswers("""
                {"answerable": false,
                 "answer": "Trích đoạn chỉ nêu số ngày nghỉ, không nói về trường hợp thử việc.",
                 "citedChunkIds": []}""");

        Answer answer = service().ask(AskQuery.of("thử việc có được nghỉ phép không?"));

        assertThat(answer.answered()).isFalse();
        assertThat(answer.answer()).contains("không nói về");
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void dropsACitationTheModelInvented() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.77));
        modelAnswers("""
                {"answerable": true, "answer": "…",
                 "citedChunkIds": [11, 999]}""");

        Answer answer = service().ask(AskQuery.of("nghỉ phép"));

        // #999 was never shown to the model; a citation with no source behind it is the
        // one thing this layer exists to prevent.
        assertThat(answer.citations()).extracting(Citation::chunkId).containsExactly(11);
    }

    @Test
    void doesNotRepeatTheSameCitation() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.77));
        modelAnswers("""
                {"answerable": true, "answer": "…", "citedChunkIds": [11, 11, 11]}""");

        assertThat(service().ask(AskQuery.of("nghỉ phép")).citations()).hasSize(1);
    }

    @Test
    void putsEveryRetrievedChunkInThePromptWithItsId() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.77),
                  hit(12, 114, null, "Ngày nghỉ tăng thêm", 0.71));
        modelAnswers("{\"answerable\": true, \"answer\": \"…\", \"citedChunkIds\": [11]}");

        service().ask(AskQuery.of("nghỉ hằng năm mấy ngày?"));

        String prompt = sent.get().getContents();
        assertThat(prompt).contains("[#11]").contains("[#12]");
        assertThat(prompt).contains("Điều 113 khoản 1-5").contains("Nghỉ hằng năm");
        assertThat(prompt).contains("nghỉ hằng năm mấy ngày?");
        // The rules that keep it from answering out of its own training.
        assertThat(prompt).contains("Chỉ trả lời dựa trên các trích đoạn");
        assertThat(prompt).contains("answerable = false");
    }

    @Test
    void passesTheRetrievalSettingsStraightThrough() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.9));
        modelAnswers("{\"answerable\": true, \"answer\": \"…\", \"citedChunkIds\": []}");

        service().ask(new AskQuery("câu hỏi", 3, 0.65, true));

        org.mockito.ArgumentCaptor<SearchQuery> captor =
                org.mockito.ArgumentCaptor.forClass(SearchQuery.class);
        verify(search).search(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(3);
        assertThat(captor.getValue().minScore()).isEqualTo(0.65);
        assertThat(captor.getValue().expandRefs()).isTrue();
    }

    @Test
    void reportsAModelFailureAsSuchRatherThanAsAnUnanswerableQuestion() {
        retrieves(hit(11, 113, "1-5", "Nghỉ hằng năm", 0.77));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("401 authentication_error"));

        // answered=false would tell the caller the corpus is silent, when in fact the
        // question was never put to the model.
        assertThatThrownBy(() -> new AskService(search, ChatClient.builder(chatModel))
                .ask(AskQuery.of("nghỉ phép")))
                .isInstanceOf(AnswerGenerationException.class)
                .hasMessageContaining("401");
    }

    @Test
    void rejectsAnEmptyQuestion() {
        assertThatThrownBy(() -> AskQuery.of("   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("question");
    }

    @Test
    void leavesCrossReferenceExpansionOffByDefault() {
        // Reference resolution misreads "Điều 2 của Luật này" inside a quoted statute,
        // and a wrong article in the context becomes a wrong citation in the answer.
        assertThat(AskQuery.of("bất kỳ").expandRefs()).isFalse();
    }
}
