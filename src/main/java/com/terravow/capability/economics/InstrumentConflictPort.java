package com.terravow.capability.economics;

import com.terravow.capability.economics.grounding.ViolationFacts;

import java.util.List;

/**
 * A retrieval seam that returns the regulatory instruments (central / state / NGT) bearing on a
 * violation fact pattern, so the Economics Engine can detect and report conflicts (Req 6.3).
 *
 * <p>In production this is backed by the same managed regulatory-corpus retrieval that
 * {@link com.terravow.capability.economics.grounding.RegulatoryGrounding} uses (Vertex AI Search /
 * BigQuery vector search); modelling it as an injected port keeps {@link DefaultEconomicsEngine}
 * deterministic and testable against controlled stubs. The engine does not decide which instruments
 * exist — it enumerates whatever the port returns and reports a conflict when two or more are
 * present (Req 6.3).
 *
 * @see InstrumentConflict
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.3</a>
 */
public interface InstrumentConflictPort {

    /**
     * Returns every regulatory instrument that bears on the fact pattern (Req 6.3).
     *
     * <p>When two or more instruments are returned they are treated as conflicting and the engine
     * enumerates every one of them in the Evidence_Chain (Req 6.3). An empty or single-element list
     * means no conflict.
     *
     * @param facts the violation fact pattern; never {@code null}
     * @return the applicable instruments; never {@code null}, may be empty
     */
    List<RegulatoryInstrument> instrumentsFor(ViolationFacts facts);
}
