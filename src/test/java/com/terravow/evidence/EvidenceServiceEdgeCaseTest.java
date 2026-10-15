package com.terravow.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.evidence.ProvenanceResult.ProvenanceStatus;
import com.terravow.gate.GateDecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Evidence/Audit edge cases of Task 3.5: provenance not-found leaving the audit
 * trail unchanged (Req 10.6), unsupported values routed to human review (Req 10.7), and the
 * audit-before-return ordering guarantee of synchronous persistence (Req 10.3).
 *
 * <p>These examples complement the Property tests for the Evidence service: they pin the specific
 * boundary behaviours that the structural safety contract depends on.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.3, 10.6, 10.7</a>
 */
class EvidenceServiceEdgeCaseTest {

    private final DefaultEvidenceService evidenceService = new DefaultEvidenceService();
    private final DefaultAuditService auditService = new DefaultAuditService();

    /** Builds a single-link Evidence_Chain that resolves to a primary Operating_Log source. */
    private static EvidenceChain resolvableChain(String refId) {
        return new EvidenceChain(
                List.of(new EvidenceLink(SourceType.OPERATING_LOG, new SourceRef(refId))));
    }

    /**
     * Req 10.6: a provenance lookup for an unknown value returns {@code NOT_FOUND} and leaves the
     * audit trail unchanged. We snapshot the audit entries before and after the lookup and assert
     * the pure read mutated nothing.
     */
    @Test
    void provenanceOfUnknownValueReturnsNotFoundAndLeavesAuditUnchanged() {
        // Seed an unrelated audit entry so "unchanged" is a meaningful, non-empty baseline.
        AuditEntry seeded =
                new AuditEntry(
                        new RecommendationId("rec-seed"),
                        GateDecision.ALLOW,
                        0.9,
                        resolvableChain("log-seed"),
                        Instant.parse("2025-01-01T00:00:00Z"));
        auditService.persistDecision(seeded);

        List<AuditEntry> before = auditService.entries();

        ProvenanceResult result = evidenceService.provenanceOf(new ValueRef("unknown-value"));

        List<AuditEntry> after = auditService.entries();

        assertThat(result.status()).isEqualTo(ProvenanceStatus.NOT_FOUND);
        assertThat(result.chain()).isEmpty();
        // The lookup is a pure read: the audit trail is identical before and after (Req 10.6).
        assertThat(after).isEqualTo(before);
        assertThat(after).containsExactly(seeded);
    }

    /**
     * Req 10.6: when a chain *is* stored, {@code provenanceOf} returns the complete stored chain and
     * still touches no audit state. This guards the contrast with the not-found path.
     */
    @Test
    void provenanceOfKnownValueReturnsStoredChainWithoutTouchingAudit() {
        ValueRef ref = new ValueRef("eid-delta");
        EvidenceChain stored = resolvableChain("log-eid-delta");
        evidenceService.record(ref, stored);

        List<AuditEntry> before = auditService.entries();

        ProvenanceResult result = evidenceService.provenanceOf(ref);

        assertThat(result.status()).isEqualTo(ProvenanceStatus.FOUND);
        assertThat(result.chain()).contains(stored);
        assertThat(auditService.entries()).isEqualTo(before);
    }

    /**
     * Req 10.7: marking a value unsupported records the reason and produces a review routed to
     * human review. The returned review carries the offending value reference, the verbatim reason,
     * and {@code routedToHumanReview == true}; no Evidence_Chain is stored for the value.
     */
    @Test
    void markUnsupportedRecordsReasonAndRoutesToHumanReview() {
        ValueRef ref = new ValueRef("unlinkable-value");
        String reason = "No primary source could be resolved for the reported figure";

        UnsupportedValueReview review = evidenceService.markUnsupported(ref, reason);

        assertThat(review.value()).isEqualTo(ref);
        assertThat(review.reason()).isEqualTo(reason);
        assertThat(review.routedToHumanReview()).isTrue();

        // An unsupported value has no stored chain: a subsequent provenance lookup is NOT_FOUND
        // (markUnsupported does not fabricate provenance).
        assertThat(evidenceService.provenanceOf(ref).status())
                .isEqualTo(ProvenanceStatus.NOT_FOUND);
    }

    /**
     * Req 10.3: {@code persistDecision} completes synchronously, so a persisted entry is queryable
     * immediately after the call returns — there is no asynchronous hand-off that could let a
     * decision-oriented output precede its audit record.
     */
    @Test
    void persistDecisionCompletesSynchronouslyAndIsQueryableImmediately() {
        RecommendationId id = new RecommendationId("rec-sync");
        AuditEntry entry =
                new AuditEntry(
                        id,
                        GateDecision.ASK,
                        0.42,
                        resolvableChain("log-sync"),
                        Instant.parse("2025-02-02T12:00:00Z"));

        auditService.persistDecision(entry);

        // Immediately after the call returns, both the index lookup and the trail reflect the write.
        assertThat(auditService.findByRecommendation(id)).contains(entry);
        assertThat(auditService.entries()).containsExactly(entry);
    }

    /**
     * Req 10.3: the audit-before-return ordering holds across multiple decisions — each persisted
     * entry is observable in insertion order the instant persistence returns.
     */
    @Test
    void persistedEntriesAreObservableInInsertionOrderImmediately() {
        AuditEntry first =
                new AuditEntry(
                        new RecommendationId("rec-1"),
                        GateDecision.ALLOW,
                        0.95,
                        resolvableChain("log-1"),
                        Instant.parse("2025-03-01T00:00:00Z"));
        AuditEntry second =
                new AuditEntry(
                        new RecommendationId("rec-2"),
                        GateDecision.BLOCK,
                        0.10,
                        resolvableChain("log-2"),
                        Instant.parse("2025-03-01T01:00:00Z"));

        auditService.persistDecision(first);
        assertThat(auditService.entries()).containsExactly(first);

        auditService.persistDecision(second);
        assertThat(auditService.entries()).containsExactly(first, second);
    }
}
