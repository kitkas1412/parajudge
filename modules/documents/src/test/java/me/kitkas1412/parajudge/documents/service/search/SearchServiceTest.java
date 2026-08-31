package me.kitkas1412.parajudge.documents.service.search;

import me.kitkas1412.parajudge.documents.entity.Article;
import me.kitkas1412.parajudge.documents.entity.Chapter;
import me.kitkas1412.parajudge.documents.entity.Chunk;
import me.kitkas1412.parajudge.documents.entity.Document;
import me.kitkas1412.parajudge.documents.repository.ArticleRepository;
import me.kitkas1412.parajudge.documents.repository.ChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Search shape and cross-reference expansion, without a database. */
class SearchServiceTest {

    private static final String HOST_LAW = "45/2019/QH14";

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final ChunkRepository chunks = mock(ChunkRepository.class);
    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final SearchService service =
            new SearchService(embeddingModel, chunks, articles, "bge-m3");

    private final Document document = new Document(HOST_LAW, "Bộ luật Lao động", null, null, null);
    private final Chapter chapter = new Chapter(document, "VII", "THỜI GIỜ NGHỈ NGƠI");

    private Article article(int dieuNo, String title, String sourceLaw) {
        return new Article(document, chapter, null, dieuNo, title,
                "Điều " + dieuNo + ". " + title, null, sourceLaw);
    }

    private Chunk chunk(int id, Article article, String khoanRange, Integer... refs) {
        Chunk chunk = new Chunk(article, "khoan_group", khoanRange,
                "Bộ luật Lao động — Điều " + article.getDieuNo(), refs, 100);
        setId(chunk, id);
        return chunk;
    }

    private void setId(Object entity, int id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void ranksByScoreAndCarriesTheCitation() {
        Article a113 = article(113, "Nghỉ hằng năm", HOST_LAW);
        when(embeddingModel.embed("nghỉ phép")).thenReturn(new float[]{0.1f, 0.2f});
        when(chunks.findNearest(anyString(), anyDouble(), anyInt())).thenReturn(List.of(
                new Object[]{1, 0.61}, new Object[]{2, 0.75}));
        when(chunks.findAllById(any())).thenReturn(List.of(
                chunk(1, a113, "6-7"), chunk(2, a113, "1-5")));

        SearchResult result = service.search(SearchQuery.of("nghỉ phép"));

        assertThat(result.model()).isEqualTo("bge-m3");
        assertThat(result.candidates()).isEqualTo(2);
        // Repository order is by id; the answer must be by score.
        assertThat(result.hits()).extracting(ChunkHit::score).containsExactly(0.75, 0.61);
        assertThat(result.hits().get(0).citation()).isEqualTo("Điều 113 khoản 1-5");
        assertThat(result.hits().get(0).articleTitle()).isEqualTo("Nghỉ hằng năm");
        assertThat(result.hits().get(0).sourceLaw()).isEqualTo(HOST_LAW);
    }

    @Test
    void citesAWholeArticleWithoutAKhoanNumber() {
        Article a1 = article(1, "Phạm vi điều chỉnh", HOST_LAW);
        Chunk whole = new Chunk(a1, "full_dieu", null, "…", new Integer[0], 50);
        setId(whole, 9);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(chunks.findNearest(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{9, 0.8}));
        when(chunks.findAllById(any())).thenReturn(List.of(whole));

        SearchResult result = service.search(SearchQuery.of("phạm vi"));

        assertThat(result.hits().get(0).citation()).isEqualTo("Điều 1");
    }

    @Test
    void sendsTheQueryVectorAsPgvectorText() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f, -0.25f});
        when(chunks.findNearest(anyString(), anyDouble(), anyInt())).thenReturn(List.of());
        when(chunks.findAllById(any())).thenReturn(List.of());

        service.search(new SearchQuery("gì đó", 3, 0.42, false));

        verify(chunks).findNearest("[0.5,-0.25]", 0.42, 3);
    }

    @Test
    void leavesReferencesAloneUnlessAsked() {
        Article a54 = article(54, "Điều kiện hưởng lương hưu", "Luật Bảo hiểm xã hội số 58/2014/QH13");
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(chunks.findNearest(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{1, 0.77}));
        when(chunks.findAllById(any())).thenReturn(List.of(chunk(1, a54, "1", 169)));

        SearchResult result = service.search(SearchQuery.of("lương hưu"));

        assertThat(result.hits().get(0).crossRefs()).containsExactly(169);
        assertThat(result.referenced()).isEmpty();
        verify(articles, org.mockito.Mockito.never())
                .findByDocumentIdAndSourceLawAndDieuNoIn(any(), any(), any());
    }

    @Test
    void followsReferencesIntoTheHostStatute() {
        // A hit on the quoted Social Insurance Law that leans on the Labour Code's
        // retirement age — the case the whole feature exists for.
        Article a54 = article(54, "Điều kiện hưởng lương hưu", "Luật Bảo hiểm xã hội số 58/2014/QH13");
        Article a169 = article(169, "Tuổi nghỉ hưu", HOST_LAW);
        setId(document, 1);
        setId(a54, 54);
        setId(a169, 169);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(chunks.findNearest(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{1, 0.77}));
        when(chunks.findAllById(any())).thenReturn(List.of(chunk(1, a54, "1", 169)));
        when(articles.findById(54)).thenReturn(java.util.Optional.of(a54));
        when(articles.findByDocumentIdAndSourceLawAndDieuNoIn(eq(1), eq(HOST_LAW), any()))
                .thenReturn(List.of(a169));

        SearchResult result = service.search(new SearchQuery("lương hưu", 5, 0.5, true));

        assertThat(result.referenced()).hasSize(1);
        ReferencedArticle pulled = result.referenced().get(0);
        assertThat(pulled.dieuNo()).isEqualTo(169);
        assertThat(pulled.title()).isEqualTo("Tuổi nghỉ hưu");
        assertThat(pulled.sourceLaw()).isEqualTo(HOST_LAW);
        assertThat(pulled.citedBy()).containsExactly(54);
    }

    @Test
    void doesNotRepeatAnArticleThatIsAlreadyAHit() {
        Article a113 = article(113, "Nghỉ hằng năm", HOST_LAW);
        Article a114 = article(114, "Ngày nghỉ hằng năm tăng thêm", HOST_LAW);
        setId(document, 1);
        setId(a113, 113);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(chunks.findNearest(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{1, 0.75}, new Object[]{2, 0.70}));
        when(chunks.findAllById(any())).thenReturn(List.of(
                chunk(1, a113, "1-5", 114), chunk(2, a114, null)));
        when(articles.findById(any())).thenReturn(java.util.Optional.of(a113));

        SearchResult result = service.search(new SearchQuery("nghỉ phép", 5, 0.5, true));

        // Điều 114 is already among the hits, so following the reference adds nothing.
        assertThat(result.referenced()).isEmpty();
    }

    @Test
    void rejectsAQueryItCannotAnswer() {
        assertThatThrownBy(() -> SearchQuery.of("  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("query");
        assertThatThrownBy(() -> new SearchQuery("x", 0, 0.5, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("topK");
        assertThatThrownBy(() -> new SearchQuery("x", 5, 1.5, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minScore");
        verifyNoInteractions(embeddingModel);
    }
}
