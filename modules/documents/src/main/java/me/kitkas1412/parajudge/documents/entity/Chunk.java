package me.kitkas1412.parajudge.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(columnDefinition = "text")
    private String chunkType;

    @Column(columnDefinition = "text")
    private String khoanRange;

    @Column(columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private Integer[] crossRefs;

    private Integer tokenCount;

    @Column(columnDefinition = "vector(1024)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    protected Chunk() {
    }

    /**
     * @param chunkType {@code full_dieu} when the whole Điều fits in one chunk,
     *                  {@code khoan_group} when it had to be split
     * @param khoanRange the Khoản this chunk covers ({@code "1"}, {@code "3-5"}),
     *                   {@code null} for a whole Điều
     * @param content the text with its parent context prefixed, which is what gets embedded
     * @param crossRefs the {@code dieu_no} this chunk points at, for expanding a retrieval hit
     */
    public Chunk(Article article, String chunkType, String khoanRange, String content,
                 Integer[] crossRefs, Integer tokenCount) {
        this.article = article;
        this.chunkType = chunkType;
        this.khoanRange = khoanRange;
        this.content = content;
        this.crossRefs = crossRefs.clone();
        this.tokenCount = tokenCount;
        article.chunks().add(this);
    }

    public Integer getId() {
        return id;
    }

    public Article getArticle() {
        return article;
    }

    public String getChunkType() {
        return chunkType;
    }

    public String getKhoanRange() {
        return khoanRange;
    }

    public String getContent() {
        return content;
    }

    public Integer[] getCrossRefs() {
        return crossRefs == null ? new Integer[0] : crossRefs.clone();
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    /** {@code null} until the embedding pass has run over this chunk. */
    public float[] getEmbedding() {
        return embedding == null ? null : embedding.clone();
    }

    /**
     * Fills in the vector. Embedding is a second pass over rows already written — the
     * ingest has to stay fast and must not fall over because a model server is down —
     * so this is the one field of a chunk that changes after it is created.
     */
    public void assignEmbedding(float[] embedding) {
        this.embedding = embedding.clone();
    }
}
