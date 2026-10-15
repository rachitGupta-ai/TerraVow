package com.terravow.reasoning.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;
import com.terravow.reasoning.ToolDeclaration;
import com.terravow.reasoning.ToolId;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultVerifiedToolRegistry}: declaration registration, dispatch by
 * {@link ToolId}, and the fail-safe UNAVAILABLE path for unknown ids and type-mismatched requests
 * (Req 2.2, 4.5, 6.2, 6.5).
 */
class DefaultVerifiedToolRegistryTest {

    private final VerifiedToolRegistry registry = DefaultVerifiedToolRegistry.withStandardTools();

    @Test
    void registersAllFourStandardDeclarations() {
        List<ToolDeclaration> declarations = registry.declarations();

        assertThat(declarations).extracting(ToolDeclaration::id)
                .containsExactlyInAnyOrder(
                        EnvironmentalCompensationTool.TOOL_ID,
                        ErdSummationTool.TOOL_ID,
                        MassPreservationTool.TOOL_ID,
                        NetPositionTool.TOOL_ID);
    }

    @Test
    void reportsRegisteredIds() {
        assertThat(registry.isRegistered(EnvironmentalCompensationTool.TOOL_ID)).isTrue();
        assertThat(registry.isRegistered(new ToolId("not.a.tool"))).isFalse();
        assertThat(registry.isRegistered(null)).isFalse();
    }

    @Test
    void dispatchesEcToolByIdAndComputes() {
        ToolResult<?> result = registry.execute(
                EnvironmentalCompensationTool.TOOL_ID,
                new EnvironmentalCompensationRequest(100L, Currency.INR, 10L, 2.0));

        assertThat(result.isComputed()).isTrue();
        assertThat(result.value().orElseThrow()).isEqualTo(new Money(2_000L, Currency.INR));
    }

    @Test
    void dispatchesNetPositionToolByIdAndComputes() {
        ToolResult<?> result = registry.execute(
                NetPositionTool.TOOL_ID,
                new NetPositionRequest(
                        new Money(10L, Currency.INR),
                        new Money(5L, Currency.INR),
                        new Money(3L, Currency.INR)));

        assertThat(result.value().orElseThrow()).isEqualTo(new Money(12L, Currency.INR));
    }

    @Test
    void unknownIdIsUnavailable() {
        ToolResult<?> result = registry.execute(new ToolId("mystery.tool"), "anything");

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.reason()).isPresent();
    }

    @Test
    void nullIdIsUnavailable() {
        ToolResult<?> result = registry.execute(null, "anything");

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void wrongRequestTypeIsUnavailableNotThrown() {
        // Hand the EC tool a net-position request; the type mismatch must degrade, not throw.
        ToolResult<?> result = registry.execute(
                EnvironmentalCompensationTool.TOOL_ID,
                new NetPositionRequest(
                        Money.zero(Currency.INR),
                        Money.zero(Currency.INR),
                        Money.zero(Currency.INR)));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.reason()).isPresent();
    }

    @Test
    void nullRequestToToolIsUnavailable() {
        ToolResult<?> result = registry.execute(ErdSummationTool.TOOL_ID, null);

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void duplicateToolIdsAreRejected() {
        assertThatThrownBy(() -> new DefaultVerifiedToolRegistry(List.of(
                new EnvironmentalCompensationTool(),
                new EnvironmentalCompensationTool())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
