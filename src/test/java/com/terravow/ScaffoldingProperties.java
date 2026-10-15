package com.terravow;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Scaffolding sanity property. Confirms the property-based testing stack (jqwik on the JUnit 5
 * platform) and AssertJ are wired and runnable, so later Correctness-Property tests have a
 * working harness.
 *
 * <p>Feature: terravow, Property 0: scaffolding harness is operational.
 */
class ScaffoldingProperties {

    // Feature: terravow, Property 0: the test harness evaluates properties over many inputs.
    @Property(tries = 100)
    void jqwikAndAssertJAreWired(@ForAll @IntRange(min = 0, max = 1_000) int value) {
        assertThat(value).isBetween(0, 1_000);
    }
}
