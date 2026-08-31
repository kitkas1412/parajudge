package me.kitkas1412.parajudge.documents.repository;

import me.kitkas1412.parajudge.documents.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Integer> {

    /** Chunks the embedding pass has not reached yet, oldest first so a resumed run is stable. */
    @Query("select c from Chunk c where c.embedding is null order by c.id")
    List<Chunk> findWithoutEmbedding();

    @Query("select count(c) from Chunk c where c.embedding is null")
    long countWithoutEmbedding();

    /**
     * Nearest neighbours of a query vector, closest first.
     *
     * <p>Native because JPQL has no {@code <=>}. The vector arrives as pgvector's own
     * text form and is cast in SQL rather than bound as an array — that keeps the query
     * free of any Hibernate vector type mapping, which lives in the {@code start} module
     * rather than here.
     *
     * <p>{@code <=>} is cosine distance, so similarity is {@code 1 - distance}; bge-m3
     * returns unit vectors, which is what makes that identity hold.
     *
     * @return rows of {@code (id, score)}, score descending
     */
    @Query(value = """
            SELECT c.id, 1 - (c.embedding <=> CAST(:vector AS vector)) AS score
            FROM chunks c
            WHERE c.embedding IS NOT NULL
              AND 1 - (c.embedding <=> CAST(:vector AS vector)) >= :minScore
            ORDER BY c.embedding <=> CAST(:vector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findNearest(@Param("vector") String vector,
                               @Param("minScore") double minScore,
                               @Param("topK") int topK);
}
