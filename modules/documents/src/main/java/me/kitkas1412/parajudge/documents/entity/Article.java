package me.kitkas1412.parajudge.documents.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    private Integer dieuNo;
    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String fullText;
    private LocalDate effectiveDate;
    @Column(columnDefinition = "text")
    private String sourceLaw;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Chunk> chunks = new ArrayList<>();

    protected Article() {
    }

    /**
     * @param section {@code null} for an article that hangs straight off its chapter
     * @param sourceLaw which statute this text belongs to — the host code for its own
     *                  articles, the amended statute for the ones Điều 219 quotes
     */
    public Article(Document document, Chapter chapter, Section section, Integer dieuNo, String title,
                   String fullText, LocalDate effectiveDate, String sourceLaw) {
        this.document = document;
        this.chapter = chapter;
        this.section = section;
        this.dieuNo = dieuNo;
        this.title = title;
        this.fullText = fullText;
        this.effectiveDate = effectiveDate;
        this.sourceLaw = sourceLaw;
        document.articles().add(this);
        chapter.articles().add(this);
        if (section != null) {
            section.articles().add(this);
        }
    }

    public Integer getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public Section getSection() {
        return section;
    }

    public Integer getDieuNo() {
        return dieuNo;
    }

    public String getTitle() {
        return title;
    }

    public String getFullText() {
        return fullText;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getSourceLaw() {
        return sourceLaw;
    }

    public List<Chunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    /** Both sides are wired by the child constructor; see {@link Chunk#Chunk}. */
    List<Chunk> chunks() {
        return chunks;
    }
}
