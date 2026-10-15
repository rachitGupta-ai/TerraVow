package com.terravow.capability.pollution;

import java.util.Objects;

/**
 * The identifier of a reading stream — a single stack, effluent outlet, or sensor emitting a
 * time-ordered series of readings (Req 3.4, 3.5).
 *
 * <p>Anomaly detection is per-stream: the expected profile is established from the history of a
 * single stream, and a stream with fewer than the configured minimum readings defers anomaly
 * evaluation as insufficient-history (Req 3.5). The identifier is a non-blank string.
 *
 * @param id a non-blank stream identifier
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 3.4, 3.5</a>
 */
public record StreamId(String id) {

    /**
     * Validates that the stream identifier is present and non-blank.
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public StreamId {
        Objects.requireNonNull(id, "StreamId id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("StreamId id must not be blank");
        }
    }
}
