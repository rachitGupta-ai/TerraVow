package com.terravow.capability.certification;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceRef;
import com.terravow.evidence.SourceType;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic implementation of {@link CertificationService} (Req 9.1–9.6).
 *
 * <p>The evaluation runs an ordered, fully deterministic algorithm — it reads no wall-clock time
 * and uses no randomness, so the same facility, effort history, and minimum observation period
 * always yield the same verdict:
 *
 * <ol>
 *   <li><b>Integrity gate (Req 9.6):</b> if the evidence contains an unverifiable reading (a point
 *       flagged {@code verified = false}) or contradictory measurement points (two points at the
 *       same instant with different metric values), certification is withheld as
 *       {@link VerdictStatus#WITHHELD_GAMING}, the facility is omitted from the registry, and the
 *       findings are recorded in the Evidence_Chain.</li>
 *   <li><b>Sufficiency gate (Req 9.2, 9.3):</b> the verdict must be derived from at least two
 *       verified measurement points separated across at least the minimum observation period. If
 *       fewer than two verified points exist, or the span between the earliest and latest verified
 *       points is shorter than {@code minObservation}, certification is withheld as
 *       {@link VerdictStatus#WITHHELD_INSUFFICIENT_HISTORY} and the evaluation is routed to human
 *       review.</li>
 *   <li><b>Gaming-pattern gate (Req 9.6):</b> if the improvement is not sustained — concentrated in
 *       a single late jump while the earlier verified trajectory regressed — the pattern is treated
 *       as consistent with metric-gaming and certification is withheld as
 *       {@link VerdictStatus#WITHHELD_GAMING}.</li>
 *   <li><b>Issue (Req 9.1, 9.4, 9.5):</b> otherwise the rate of improvement across the verified
 *       series is positive and sustained; a certificate is issued, persisted with its confidence
 *       and Evidence_Chain, and published to the public global registry.</li>
 * </ol>
 *
 * <p>The {@link CertificateStore} and {@link PublicRegistry} are injected ports (Req 9.4, 9.5). The
 * minimum observation period is supplied per evaluation (Req 9.1, 9.3), and the certificate
 * validity duration is injected so it is configurable without wall-clock dependence; the validity
 * window is anchored to the latest verified observation so the verdict stays deterministic.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 9.1, 9.2, 9.3, 9.4,
 *      9.5, 9.6</a>
 */
public final class DefaultCertificationService implements CertificationService {

    /** Reference prefix for generated certificate references. */
    static final String CERT_REF_PREFIX = "cert";

    /** Confidence assigned to an issued certificate under a sustained verified trajectory. */
    static final Confidence ISSUED_CONFIDENCE = new Confidence(0.85);

    /** Confidence assigned to a withheld-for-insufficient-history verdict. */
    static final Confidence INSUFFICIENT_HISTORY_CONFIDENCE = new Confidence(0.5);

    /** Confidence assigned to a withheld-for-gaming verdict (high confidence in the withholding). */
    static final Confidence GAMING_CONFIDENCE = new Confidence(0.9);

    private final CertificateStore certificateStore;
    private final PublicRegistry publicRegistry;
    private final Duration certificateValidity;

    /**
     * Constructs the service with its injected persistence and publication ports and a configurable
     * certificate validity duration (Req 9.4, 9.5).
     *
     * @param certificateStore    the store to persist issued certificates into; never {@code null}
     * @param publicRegistry      the public global registry to publish into; never {@code null}
     * @param certificateValidity the validity-window length of an issued certificate; never
     *                            {@code null}, must be positive
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code certificateValidity} is not positive
     */
    public DefaultCertificationService(
            CertificateStore certificateStore,
            PublicRegistry publicRegistry,
            Duration certificateValidity) {
        this.certificateStore = Objects.requireNonNull(certificateStore, "certificateStore");
        this.publicRegistry = Objects.requireNonNull(publicRegistry, "publicRegistry");
        Objects.requireNonNull(certificateValidity, "certificateValidity");
        if (certificateValidity.isNegative() || certificateValidity.isZero()) {
            throw new IllegalArgumentException(
                    "certificateValidity must be positive, got: " + certificateValidity);
        }
        this.certificateValidity = certificateValidity;
    }

    /** {@inheritDoc} */
    @Override
    public CertificationVerdict evaluate(
            FacilityId facility, EffortHistory history, Period minObservation) {
        Objects.requireNonNull(facility, "facility must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(minObservation, "minObservation must not be null");
        if (minObservation.isNegative() || minObservation.isZero()) {
            throw new IllegalArgumentException(
                    "minObservation must be positive, got: " + minObservation);
        }
        if (!history.facility().equals(facility)) {
            throw new IllegalArgumentException(
                    "history belongs to a different facility: expected " + facility
                            + ", got " + history.facility());
        }

        // Step 1 — Integrity gate (Req 9.6): unverifiable readings or contradictory points.
        if (hasUnverifiableReading(history)) {
            return CertificationVerdict.gaming(
                    GAMING_CONFIDENCE,
                    findingChain(history, "unverifiable-reading",
                            "verified effort history contains a reading that could not be verified"
                                    + " against its primary source"));
        }
        if (hasContradictoryPoints(history)) {
            return CertificationVerdict.gaming(
                    GAMING_CONFIDENCE,
                    findingChain(history, "contradictory-measurements",
                            "two measurement points at the same instant report conflicting"
                                    + " improvement metrics"));
        }

        List<MeasurementPoint> verified = history.verifiedPoints();

        // Step 2 — Sufficiency gate (Req 9.2, 9.3): >= 2 verified points spanning >= minObservation.
        if (verified.size() < 2 || !spansMinObservation(verified, minObservation)) {
            return CertificationVerdict.insufficientHistory(
                    INSUFFICIENT_HISTORY_CONFIDENCE,
                    findingChain(history, "insufficient-history",
                            "verified effort history spans less than the configured minimum"
                                    + " trajectory observation period of " + minObservation));
        }

        // Step 3 — Gaming-pattern gate (Req 9.6): improvement not sustained across the period.
        if (isGamingPattern(verified)) {
            return CertificationVerdict.gaming(
                    GAMING_CONFIDENCE,
                    findingChain(history, "unsustained-improvement",
                            "improvement is concentrated in a late jump while the earlier verified"
                                    + " trajectory regressed, a pattern consistent with"
                                    + " metric-gaming"));
        }

        // Step 4 — Issue (Req 9.1, 9.4, 9.5): sustained, positive rate of improvement.
        MeasurementPoint earliest = verified.get(0);
        MeasurementPoint latest = verified.get(verified.size() - 1);
        double rateOfImprovement = latest.improvementMetric() - earliest.improvementMetric();
        if (rateOfImprovement <= 0.0) {
            // No net improvement over the period: withhold rather than certify a non-improving
            // facility (Req 9.1 — certification rewards real improvement trajectory).
            return CertificationVerdict.gaming(
                    GAMING_CONFIDENCE,
                    findingChain(history, "no-net-improvement",
                            "verified trajectory shows no net improvement across the observation"
                                    + " period"));
        }

        Instant validFrom = latest.observedAt();
        Instant validUntil = validFrom.plus(certificateValidity);
        EvidenceChain evidence = issuedChain(verified);
        Certificate certificate = new Certificate(
                certificateReference(facility, earliest.observedAt(), latest.observedAt()),
                facility,
                validFrom,
                validUntil,
                ISSUED_CONFIDENCE,
                evidence);

        // Persist certificate + confidence + Evidence_Chain (Req 9.4) before publishing (Req 9.5).
        certificateStore.save(certificate);
        publicRegistry.publish(RegistryEntry.from(certificate));

        return CertificationVerdict.issued(certificate);
    }

    /**
     * Detects any reading that could not be verified against its primary source (Req 9.6).
     *
     * @param history the effort history
     * @return {@code true} if any point is flagged unverified
     */
    private static boolean hasUnverifiableReading(EffortHistory history) {
        return history.points().stream().anyMatch(p -> !p.verified());
    }

    /**
     * Detects contradictory measurement points: two points at the same instant reporting different
     * improvement metrics (Req 9.6). Points are already sorted chronologically by
     * {@link EffortHistory}.
     *
     * @param history the effort history
     * @return {@code true} if a same-instant conflict is found
     */
    private static boolean hasContradictoryPoints(EffortHistory history) {
        List<MeasurementPoint> points = history.points();
        for (int i = 1; i < points.size(); i++) {
            MeasurementPoint prev = points.get(i - 1);
            MeasurementPoint cur = points.get(i);
            if (prev.observedAt().equals(cur.observedAt())
                    && prev.improvementMetric() != cur.improvementMetric()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the verified series spans at least the minimum observation period (Req 9.2,
     * 9.3). The period is resolved against the earliest observation instant so the comparison is
     * deterministic and calendar-correct.
     *
     * @param verified       the chronologically-ordered verified points (size &gt;= 2)
     * @param minObservation the configured minimum observation period
     * @return {@code true} if {@code latest - earliest >= minObservation}
     */
    private static boolean spansMinObservation(
            List<MeasurementPoint> verified, Period minObservation) {
        Instant earliest = verified.get(0).observedAt();
        Instant latest = verified.get(verified.size() - 1).observedAt();
        Instant minimumEnd = earliest.atZone(java.time.ZoneOffset.UTC)
                .plus(minObservation)
                .toInstant();
        return !latest.isBefore(minimumEnd);
    }

    /**
     * Detects a gaming pattern: the improvement is concentrated in a single late jump while the
     * earlier verified trajectory regressed (Req 9.6). Concretely, with the verified series
     * {@code p0..pn}, the trajectory up to the second-to-last point ({@code p0..p[n-1]}) shows a net
     * regression (metric declined) yet the final point jumps above the earliest — i.e. the apparent
     * improvement did not accrue over the period but appeared only at the last audit point.
     *
     * @param verified the chronologically-ordered verified points (size &gt;= 2)
     * @return {@code true} if the late-jump gaming pattern is present
     */
    private static boolean isGamingPattern(List<MeasurementPoint> verified) {
        int n = verified.size();
        if (n < 3) {
            return false; // need an interior trajectory to detect a late jump
        }
        double first = verified.get(0).improvementMetric();
        double penultimate = verified.get(n - 2).improvementMetric();
        double last = verified.get(n - 1).improvementMetric();
        boolean earlierRegressed = penultimate < first;
        boolean lateJumpAboveStart = last > first;
        return earlierRegressed && lateJumpAboveStart;
    }

    /**
     * Builds a deterministic certificate reference from the facility and the verified-series bounds
     * so re-evaluating the same inputs yields the same reference (idempotent persistence /
     * publication).
     *
     * @param facility the certified facility
     * @param from     the earliest verified observation instant
     * @param to       the latest verified observation instant
     * @return a non-blank certificate reference
     */
    private static String certificateReference(FacilityId facility, Instant from, Instant to) {
        return CERT_REF_PREFIX + ":" + facility.value()
                + ":" + from.toEpochMilli() + ":" + to.toEpochMilli();
    }

    /**
     * Builds an Evidence_Chain for an issued verdict, linking each verified measurement point's
     * primary source (Req 9.4).
     *
     * @param verified the verified measurement points
     * @return the Evidence_Chain of verified-point sources
     */
    private static EvidenceChain issuedChain(List<MeasurementPoint> verified) {
        EvidenceChain chain = EvidenceChain.EMPTY;
        for (MeasurementPoint p : verified) {
            chain = chain.with(p.source());
        }
        return chain;
    }

    /**
     * Builds an Evidence_Chain recording a withholding finding, linking the finding and every
     * supporting measurement-point source (Req 9.6). The finding itself is recorded as a
     * {@link SourceType#OPERATING_LOG} link so it resolves within the provenance spine.
     *
     * @param history    the evaluated effort history
     * @param findingId  a short finding identifier
     * @param findingText a human-readable description of the finding
     * @return the Evidence_Chain recording the finding and its supporting points
     */
    private static EvidenceChain findingChain(
            EffortHistory history, String findingId, String findingText) {
        EvidenceLink finding = new EvidenceLink(
                SourceType.OPERATING_LOG,
                new SourceRef("certification-finding:" + findingId + ":"
                        + history.facility().value() + ":" + findingText));
        EvidenceChain chain = EvidenceChain.EMPTY.with(finding);
        for (MeasurementPoint p : history.points()) {
            chain = chain.with(p.source());
        }
        return chain;
    }
}
