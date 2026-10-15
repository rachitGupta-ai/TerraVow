package com.terravow.capability.pollution;

import com.terravow.evidence.EvidenceChain;
import com.terravow.evidence.EvidenceLink;
import com.terravow.evidence.SourceType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic implementation of the {@link CrossMediaGuardrail} (Req 3.6, 3.7).
 *
 * <p>The guardrail aggregates the total measured pollutant load per {@link EnvironmentalMedium}
 * across the before readings and, independently, across the after readings. It then compares, for
 * every medium observed in either set, the after load to the before load (a medium absent from a
 * set contributes a load of zero). The remediation is flagged as cross-media displacement if and
 * only if at least one medium's load strictly decreased and at least one <em>other</em> medium's
 * load strictly increased (Req 3.6, 3.7) — the precise trade-off that means harm was relocated
 * rather than removed.
 *
 * <p>When displacement is flagged, the supporting readings (all before and after readings that
 * evidence the comparison) and an Evidence_Chain linking to each reading are attached (Req 3.7).
 * The computation reads no wall-clock time and uses no randomness; it iterates a fixed-order
 * {@link EnumMap} so the result is deterministic for a given input.
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.6, 3.7</a>
 */
public final class DefaultCrossMediaGuardrail implements CrossMediaGuardrail {

    /** {@inheritDoc} */
    @Override
    public CrossMediaResult assess(
            Remediation remediation, List<Reading> before, List<Reading> after) {
        Objects.requireNonNull(remediation, "remediation must not be null");
        Objects.requireNonNull(before, "before must not be null");
        Objects.requireNonNull(after, "after must not be null");

        Map<EnvironmentalMedium, Double> beforeLoad = aggregateByMedium(before);
        Map<EnvironmentalMedium, Double> afterLoad = aggregateByMedium(after);

        boolean anyDecrease = false;
        boolean anyIncrease = false;
        for (EnvironmentalMedium medium : EnvironmentalMedium.values()) {
            double b = beforeLoad.getOrDefault(medium, 0.0);
            double a = afterLoad.getOrDefault(medium, 0.0);
            if (a < b) {
                anyDecrease = true;
            } else if (a > b) {
                anyIncrease = true;
            }
        }

        // Req 3.6, 3.7 — displacement iff load fell in one medium and rose in another.
        boolean displacement = anyDecrease && anyIncrease;

        if (!displacement) {
            return new CrossMediaResult(
                    remediation, false, List.of(), EvidenceChain.EMPTY);
        }

        List<Reading> supporting = new ArrayList<>(before.size() + after.size());
        supporting.addAll(before);
        supporting.addAll(after);

        EvidenceChain chain = EvidenceChain.EMPTY;
        for (Reading r : supporting) {
            chain = chain.with(new EvidenceLink(SourceType.READING, r.source()));
        }
        return new CrossMediaResult(remediation, true, supporting, chain);
    }

    /**
     * Sums the measured values of the given readings per environmental medium.
     *
     * @param readings the readings to aggregate; must not contain {@code null}
     * @return the total load per medium
     * @throws NullPointerException if a reading is {@code null}
     */
    private static Map<EnvironmentalMedium, Double> aggregateByMedium(List<Reading> readings) {
        Map<EnvironmentalMedium, Double> load = new EnumMap<>(EnvironmentalMedium.class);
        for (Reading r : readings) {
            Objects.requireNonNull(r, "reading must not be null");
            load.merge(r.medium(), r.measuredValue(), Double::sum);
        }
        return load;
    }
}
