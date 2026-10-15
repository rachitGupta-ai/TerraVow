package com.terravow.capability.economics;

/**
 * A retrieval seam that assesses the eligibility of a single incentive category for an asset
 * context, returning a complete {@link IncentiveItem} (Req 6.4).
 *
 * <p>Eligibility reasoning for PAT/ESCerts, Income Tax Act Section 32 depreciation, state
 * industrial-policy incentives, and avoided-EC is grounded in real regulatory and incentive
 * instruments; modelling it behind an injected port keeps {@link DefaultEconomicsEngine}
 * deterministic and testable against controlled stubs while the live grounding is a later concern.
 * The engine queries this port once per {@link IncentiveKind} so that every category is covered
 * (Req 6.4).
 *
 * @see IncentiveAssessment
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 6.4</a>
 */
public interface IncentivePort {

    /**
     * Assesses eligibility of one incentive category for the asset context (Req 6.4).
     *
     * <p>The returned item must report the supplied {@code kind}; an eligible item carries the
     * incentive value in the context currency, an ineligible item a recorded reason. The avoided-EC
     * value, when eligible, should equal the EC exposure the asset avoids (Req 6.4, 6.5).
     *
     * @param kind    the incentive category to assess; never {@code null}
     * @param context the asset context; never {@code null}
     * @return a complete eligibility item for {@code kind}; never {@code null}
     */
    IncentiveItem assess(IncentiveKind kind, AssetContext context);
}
