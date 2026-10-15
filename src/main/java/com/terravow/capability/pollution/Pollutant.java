package com.terravow.capability.pollution;

import java.util.Objects;

/**
 * A monitored pollutant species, identified by a stable, non-blank name (e.g. {@code "PM2.5"},
 * {@code "SOx"}, {@code "COD"}).
 *
 * <p>A pollutant paired with an {@link EnvironmentalMedium} is the key under which an applicable
 * regulatory threshold is looked up (Req 3.1) and under which a threshold-violation condition is
 * reported (Req 3.3). The name is compared case-insensitively via {@link #key()} so that lookups
 * are insensitive to incidental casing differences in ingested readings.
 *
 * @param name a non-blank pollutant name
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 3.3</a>
 */
public record Pollutant(String name) {

    /**
     * Validates that the pollutant name is present and non-blank (Req 3.1).
     *
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public Pollutant {
        Objects.requireNonNull(name, "Pollutant name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Pollutant name must not be blank");
        }
    }

    /**
     * Returns the case-insensitive, trimmed lookup key for this pollutant.
     *
     * @return the normalised key used for threshold lookup
     */
    public String key() {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
