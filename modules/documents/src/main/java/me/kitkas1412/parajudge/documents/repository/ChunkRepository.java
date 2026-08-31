package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Integer> {

    /** Chunks the embedding pass has not reached yet, oldest first so a resumed run is stable. */
    @Query("select c from Chunk c where c.embedding is null order by c.id")
    List<Chunk> findWithoutEmbedding();

    @Query("select count(c) from Chunk c where c.embedding is null")
    long countWithoutEmbedding();
}
