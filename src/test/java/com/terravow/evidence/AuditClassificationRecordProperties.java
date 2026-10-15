package com.terravow.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.gate.GateDecision;
import java.time.Instant;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for the completeness of the decision audit trail recorded by
 * {@link DefaultAuditService}.
 *
 * <p>Validates Correctness Property 32: for any recommendation classified by the Decision_Gate, the
 * audit-trail entry contains the recommendation identifier, the ALLOW / ASK / BLOCK classification,
 * the confidence value, and the Evidence_Chain supporting the recommendation (Req 10.2).
 *
 * <p>The invariant is checked by persisting an arbitrary {@link AuditEntry} — built over an
 * arbitrary recommendation id, gate decision, confidence in {@code [0,1]}, Evidence_Chain, and
 * instant — and then reading it back via {@link AuditService#findByRecommendation(RecommendationId)}.
 * The round-tripped entry must be present and must carry every one of the four recorded fields
 * unchanged, so no part of the classification is dropped or altered in the audit record.
 *
 * <p><strong>Validates: Requirements 10.2</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 10.2</a>
 */
class AuditClassificationRecordProperties {

    // Feature: terravow, Property 32: Classification is recorded completely in the audit trail
    @Property(tries = 100)
    void persistedClassificationRoundTripsAllRecordedFields(@ForAll("auditEntries") AuditEntry entry) {
        AuditService audit = new DefaultAuditService();

        audit.persistDecision(entry);

        AuditEntry stored = audit.findByRecommendation(entry.id())
                .orElseThrow(() -> new AssertionError(
                        "classification was not recorded in the audit trail for " + entry.id()));

        // Every one of the four fields Req 10.2 mandates is recorded completely and unchanged:
        // the recommendation identifier, the ALLOW/ASK/BLOCK classification, the confidence value,
        // and the Evidence_Chain supporting the recommendation.
        assertThat(stored.id()).isEqualTo(entry.id());
        assertThat(stored.decision()).isEqualTo(entry.decision());
        assertThat(stored.confidence()).isEqualTo(entry.confidence());
        assertThat(stored.evidence()).isEqualTo(entry.evidence());

        // The whole entry round-trips, and it is the one entry on the trail.
        assertThat(stored).isEqualTo(entry);
        assertThat(audit.entries()).containsExactly(entry);
    }

    /** Arbitrary {@link AuditEntry} spanning the full recorded-field space of Req 10.2. */
    @Provide
    Arbitrary<AuditEntry> auditEntries() {
        return Combinators.combine(
                        recommendationIds(),
                        Arbitraries.of(GateDecision.class),
                        confidences(),
                        evidenceChains(),
                        instants())
                .as(AuditEntry::new);
    }

    private Arbitrary<RecommendationId> recommendationIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(32)
                .map(RecommendationId::new);
    }

    private Arbitrary<Double> confidences() {
        return Arbitraries.doubles().between(0.0, 1.0).filter(Double::isFinite);
    }

    private Arbitrary<Instant> instants() {
        return Arbitraries.longs()
                .between(0L, 4_102_444_800L) // epoch .. year 2100, in seconds
                .map(Instant::ofEpochSecond);
    }

    /** Evidence_Chains ranging from empty to a few links across all source types. */
    private Arbitrary<EvidenceChain> evidenceChains() {
        return evidenceLinks().list().ofMinSize(0).ofMaxSize(5).map(EvidenceChain::new);
    }

    private Arbitrary<EvidenceLink> evidenceLinks() {
        Arbitrary<SourceType> types = Arbitraries.of(SourceType.class);
        Arbitrary<SourceRef> refs = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(24)
                .map(SourceRef::new);
        return Combinators.combine(types, refs).as(EvidenceLink::new);
    }
}
