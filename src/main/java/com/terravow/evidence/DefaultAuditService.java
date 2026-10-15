package com.terravow.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/**
 * In-memory implementation of {@link AuditService} (Req 10.2, 10.3).
 *
 * <p>{@link #persistDecision(AuditEntry)} writes to the backing store on the calling thread and
 * returns only after the write has completed. Because the write is synchronous and the method does
 * not return until it finishes, a caller that persists an entry and then returns a
 * decision-oriented output structurally satisfies "persist before return" (Req 10.3): there is no
 * asynchronous hand-off, queue, or background flush that could let an output precede its audit
 * record.
 *
 * <p>Durable Firestore persistence with the ≥ 7-year retention policy (Req 10.4) is deferred to the
 * storage task; this implementation keeps the audit trail in memory while preserving the ordering
 * guarantee and the recorded-fields contract (Req 10.2).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.2, 10.3, 10.4</a>
 */
@Service
public class DefaultAuditService implements AuditService {

    /** Insertion-ordered trail; append-only. */
    private final List<AuditEntry> trail = new CopyOnWriteArrayList<>();

    /** Index by recommendation id for direct lookup; last write wins per id. */
    private final ConcurrentMap<RecommendationId, AuditEntry> byRecommendation =
            new ConcurrentHashMap<>();

    @Override
    public void persistDecision(AuditEntry entry) {
        Objects.requireNonNull(entry, "AuditEntry must not be null");
        // Synchronous write: both structures are updated before this method returns, so no
        // decision-oriented output can be returned ahead of its audit record (Req 10.3).
        trail.add(entry);
        byRecommendation.put(entry.id(), entry);
    }

    @Override
    public Optional<AuditEntry> findByRecommendation(RecommendationId id) {
        Objects.requireNonNull(id, "RecommendationId must not be null");
        return Optional.ofNullable(byRecommendation.get(id));
    }

    @Override
    public List<AuditEntry> entries() {
        return List.copyOf(trail);
    }
}
