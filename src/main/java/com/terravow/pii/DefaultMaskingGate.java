package com.terravow.pii;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic implementation of {@link MaskingGate} that masks PII and confidential supplier
 * identifiers with a stable, irreversible token before any data reaches reasoning, a managed Google
 * service, or persistence (Req 13.3, 13.4).
 *
 * <h2>Deterministic, irreversible masking</h2>
 * Each sensitive field value is replaced by {@code MASK_PREFIX + SHA-256(salt || value)} rendered as
 * lowercase hex. SHA-256 is a one-way hash, so the mask is <em>irreversible</em> — the raw PII or
 * confidential identifier cannot be recovered from it. Hashing is a pure function of the configured
 * salt and the input value, so the mask is <em>deterministic and stable</em>: the same input always
 * yields the same mask (equal identifiers across records map to equal tokens, which preserves
 * join/dedup utility without exposing the raw value). The gate reads no wall-clock time and uses no
 * randomness.
 *
 * <h2>The deterministic masker is authoritative</h2>
 * An optional {@link MaskingTriage} (a Gemini Flash advisory pass) may suggest <em>additional</em>
 * sensitive fields. The suggestion is union-ed into the caller's declared sensitive set — it can
 * only cause <em>more</em> masking, never less, and can never let a declared-sensitive field pass
 * unmasked (design: "Deterministic masking remains authoritative; triage never bypasses masking").
 * A triage that throws is swallowed and treated as suggesting nothing, so an advisory failure never
 * weakens or blocks the authoritative deterministic masking.
 *
 * <h2>Fail-closed rejection</h2>
 * If masking cannot complete — the hashing primitive is unavailable, or an advisory triage names a
 * field that does not exist in the record — the gate returns {@link MaskingResult.Rejected} with a
 * recorded reason and masks/persists/sends nothing (Req 13.4).
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 13.3, 13.4</a>
 */
public final class DefaultMaskingGate implements MaskingGate {

    /** Prefix on every masked token so masked values are visibly distinguishable from raw ones. */
    static final String MASK_PREFIX = "mask:";

    /** Hashing algorithm providing the one-way (irreversible) deterministic mask. */
    private static final String HASH_ALGORITHM = "SHA-256";

    private final byte[] salt;
    private final MaskingTriage triage;

    /**
     * Creates a gate with the given salt and advisory triage.
     *
     * @param salt   a non-blank salt mixed into every hash so masked tokens are not trivially
     *               reversible via a precomputed rainbow table; the <em>same</em> salt must be used
     *               across the deployment for masks to remain stable (deterministic)
     * @param triage the advisory Flash triage seam; never {@code null} (use
     *               {@link MaskingTriage#DISABLED} for none) — advisory only, never authoritative
     * @throws NullPointerException     if {@code salt} or {@code triage} is {@code null}
     * @throws IllegalArgumentException if {@code salt} is blank
     */
    public DefaultMaskingGate(String salt, MaskingTriage triage) {
        Objects.requireNonNull(salt, "salt must not be null");
        this.triage = Objects.requireNonNull(triage, "triage must not be null");
        if (salt.isBlank()) {
            throw new IllegalArgumentException("salt must not be blank");
        }
        this.salt = salt.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convenience constructor with no advisory triage ({@link MaskingTriage#DISABLED}); the
     * deterministic masker operates on the caller's declared sensitive fields alone.
     *
     * @param salt the hashing salt; never {@code null}, non-blank
     */
    public DefaultMaskingGate(String salt) {
        this(salt, MaskingTriage.DISABLED);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the authoritative sensitive-field set (declared ∪ advisory), then deterministically
     * masks each sensitive field. Any failure to complete masking yields a recorded rejection
     * (Req 13.4) rather than a partially-masked payload.
     */
    @Override
    public MaskingResult mask(SupplierData data) {
        Objects.requireNonNull(data, "data must not be null");

        // Advisory triage is union-in only. A triage failure must never block the authoritative
        // deterministic masker, so swallow it and proceed as if it suggested nothing.
        Set<String> sensitive = new TreeSet<>(data.sensitiveFields());
        TriageHint hint;
        try {
            hint = triage.triage(data);
        } catch (RuntimeException advisoryFailure) {
            hint = TriageHint.NONE;
        }
        for (String suggested : hint.additionalSensitiveFields()) {
            // Only honour a suggestion for a field that actually exists; a hint naming a missing
            // field is ignored (it cannot name raw data to leak), and never causes a rejection.
            if (data.fields().containsKey(suggested)) {
                sensitive.add(suggested);
            }
        }

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException cannotMask) {
            // The masking primitive is unavailable: masking cannot complete. Reject with a recorded
            // reason — reason over, persist, and send to a managed service exactly nothing.
            return new MaskingResult.Rejected(
                    data.recordId(),
                    "masking could not complete: hashing algorithm "
                            + HASH_ALGORITHM + " unavailable");
        }

        // Deterministically mask every sensitive field; carry non-sensitive fields through
        // unchanged. Insertion order is preserved only for readability; it does not affect masks.
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : data.fields().entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            masked.put(name, sensitive.contains(name) ? maskValue(digest, value) : value);
        }

        return new MaskingResult.Masked(data.recordId(), masked);
    }

    /**
     * Computes the deterministic, irreversible mask for a single value: {@code MASK_PREFIX} followed
     * by the lowercase hex of {@code SHA-256(salt || value)}.
     *
     * @param digest a {@link MessageDigest} for {@link #HASH_ALGORITHM}; reset before use
     * @param value  the raw value to mask; never {@code null}
     * @return the stable masked token for {@code value}
     */
    private String maskValue(MessageDigest digest, String value) {
        digest.reset();
        digest.update(salt);
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return MASK_PREFIX + HexFormat.of().formatHex(hash);
    }
}
