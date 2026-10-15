package com.terravow.ingest;

import com.terravow.pii.MaskingResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A deterministic in-memory {@link BigQueryLandingPort} for local-first operation and tests
 * (Req 3.1, 13.3).
 *
 * <p>TerraVow is local-first and has no live BigQuery in the dev/test environment, so this stub
 * records every landed row, in landing order, as a {@link LandedRow}. Because the port only accepts
 * {@link MaskingResult.Masked} payloads, every recorded row holds masked fields only — the stub
 * never sees, and therefore cannot store, a raw identifier (Req 13.3). The backing list is a
 * {@link CopyOnWriteArrayList} so the stub is safe to share across concurrent landing writes.
 *
 * @see BigQueryLandingPort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1, 13.3</a>
 */
public final class InMemoryBigQueryLandingPort implements BigQueryLandingPort {

    private final List<LandedRow> rows = new CopyOnWriteArrayList<>();

    /** {@inheritDoc} */
    @Override
    public void land(StreamEvent.Kind kind, MaskingResult.Masked masked) {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(masked, "masked must not be null");
        rows.add(new LandedRow(kind, masked.recordId(), masked.maskedFields()));
    }

    /**
     * Returns the rows landed so far, in landing order (Req 3.1).
     *
     * @return an immutable snapshot of the landed rows; never {@code null}
     */
    public List<LandedRow> rows() {
        return List.copyOf(rows);
    }

    /**
     * A single row persisted into the landing table, carrying only masked field values (Req 13.3).
     *
     * @param kind         the event kind column; never {@code null}
     * @param recordId     the correlation id of the originating event; never {@code null}
     * @param maskedFields the masked field values; never {@code null}, immutable
     */
    public record LandedRow(StreamEvent.Kind kind, String recordId, Map<String, String> maskedFields) {

        /**
         * Validates and defensively copies the landed row.
         *
         * @throws NullPointerException if any argument, key, or value is {@code null}
         */
        public LandedRow {
            Objects.requireNonNull(kind, "LandedRow kind must not be null");
            Objects.requireNonNull(recordId, "LandedRow recordId must not be null");
            Objects.requireNonNull(maskedFields, "LandedRow maskedFields must not be null");
            maskedFields = Map.copyOf(maskedFields);
        }
    }
}
