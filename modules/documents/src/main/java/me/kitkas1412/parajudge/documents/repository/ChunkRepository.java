package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<Chunk, Integer> {
}
