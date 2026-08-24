package me.kitkas1412.parajudge.documents.entity;

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

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chapters")
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(columnDefinition = "text")
    private String chapterNo;

    @Column(columnDefinition = "text")
    private String title;

    @OneToMany(mappedBy = "chapter", fetch = FetchType.LAZY)
    private List<Section> sections = new ArrayList<>();

    @OneToMany(mappedBy = "chapter", fetch = FetchType.LAZY)
    private List<Article> articles = new ArrayList<>();

    protected Chapter() {
    }
}