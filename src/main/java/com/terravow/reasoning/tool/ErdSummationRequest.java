package com.terravow.reasoning.tool;

import java.util.List;
import java.util.Objects;

/**
 * Input arguments for the ERD summation tool (Req 2.2).
 *
 * <p>Carries the declared result {@link com.terravow.domain.Currency} and the list of
 * {@link ErdComponent}s. ERD is reported as a single monetary amount in one declared currency
 * (Req 2.1); the summation adds only the <em>available</em> components (Req 2.2) and the tool
 * verifies every available component shares the declared currency.
 *
 * @param currency   the declared result currency; may be {@code null}, surfaced as unavailable by
 *                   the tool
 * @param components the ERD components, available and unavailable; defensively copied
 *
 * @see ErdSummationTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 2.1, 2.2</a>
 */
public record ErdSummationRequest(com.terravow.domain.Currency currency, List<ErdComponent> components) {

    /**
     * Defensively copies the component list (null-tolerant for the tool to validate).
     *
     * @throws NullPointerException if a non-null {@code components} list contains a {@code null}
     */
    public ErdSummationRequest {
        if (components != null) {
            components = List.copyOf(components);
            components.forEach(c -> Objects.requireNonNull(c, "ERD component must not be null"));
        }
    }
}
