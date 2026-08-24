package me.kitkas1412.parajudge.documents.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "text")
    private String code;

    @Column(columnDefinition = "text")
    private String title;
    private LocalDate issuedDate;
    private LocalDate effectiveDate;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode amendedBy;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Chapter> chapters = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Article> articles = new ArrayList<>();

    protected Document() {
    }

    public Document(String code, String title, LocalDate issuedDate, LocalDate effectiveDate,
                    JsonNode amendedBy) {
        this.code = code;
        this.title = title;
        this.issuedDate = issuedDate;
        this.effectiveDate = effectiveDate;
        this.amendedBy = amendedBy;
    }

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public JsonNode getAmendedBy() {
        return amendedBy;
    }

    public List<Chapter> getChapters() {
        return Collections.unmodifiableList(chapters);
    }

    public List<Article> getArticles() {
        return Collections.unmodifiableList(articles);
    }

    /** Both sides are wired by the child constructor; see {@link Chapter#Chapter}. */
    List<Chapter> chapters() {
        return chapters;
    }

    List<Article> articles() {
        return articles;
    }
}
