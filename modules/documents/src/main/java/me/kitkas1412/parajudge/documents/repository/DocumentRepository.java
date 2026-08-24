package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Integer> {

    /** The code ({@code 45/2019/QH14}) is how the same statute is recognised across ingests. */
    Optional<Document> findByCode(String code);
}
