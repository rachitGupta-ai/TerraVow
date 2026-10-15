package com.terravow.pii;

/**
 * Optional advisory seam for a Gemini Flash masking-triage pass (Req 13.3).
 *
 * <p>The design routes high-volume confidentiality/masking triage to the Gemini Flash tier, but is
 * explicit that <strong>the deterministic masker is authoritative and triage never bypasses
 * masking</strong>. This interface models that seam so the {@link DefaultMaskingGate} can optionally
 * consult a Flash classifier for <em>additional</em> candidate sensitive fields, while the
 * deterministic gate alone decides what is masked and whether to reject.
 *
 * <p>Because triage is advisory, an implementation that fails or is unavailable must not block
 * masking: callers should degrade to the {@link #DISABLED} no-op, which suggests nothing and lets
 * the deterministic masker proceed on the caller's declared sensitive fields.
 *
 * @see TriageHint
 * @see DefaultMaskingGate
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3</a>
 */
@FunctionalInterface
public interface MaskingTriage {

    /**
     * The no-op triage: suggests no additional fields. Used when no Flash triage is configured or
     * when the advisory pass is unavailable, so the deterministic masker remains the sole,
     * authoritative decision-maker.
     */
    MaskingTriage DISABLED = data -> TriageHint.NONE;

    /**
     * Advises which additional fields may carry PII or confidential identifiers (Req 13.3).
     *
     * <p>The returned hint is a union-in suggestion only; it can cause the deterministic masker to
     * mask more, never less. It must never be interpreted as permission to leave a declared
     * sensitive field unmasked.
     *
     * @param data the raw supplier data under consideration; never {@code null}
     * @return an advisory hint of additional sensitive field names; never {@code null}
     */
    TriageHint triage(SupplierData data);
}
