package com.uptimecrew.expense.repository;

import com.uptimecrew.expense.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // Native query: FOR UPDATE SKIP LOCKED isn't expressible via JPQL/derived
    // queries. SKIP LOCKED lets multiple poller instances run concurrently
    // without blocking on each other's in-flight rows. Must be called inside
    // an existing @Transactional method — the row locks are held for the
    // life of that transaction.
    @Query(value = """
            SELECT * FROM expense.outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("limit") int limit);
}
