package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Integer> {
}
