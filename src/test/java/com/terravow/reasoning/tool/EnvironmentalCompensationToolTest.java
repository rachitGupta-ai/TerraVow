package com.terravow.reasoning.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EnvironmentalCompensationTool}: the documented EC formula
 * {@code base × days × deterrence} and the UNAVAILABLE-on-failure path (Req 6.2).
 */
class EnvironmentalCompensationToolTest {

    private final EnvironmentalCompensationTool tool = new EnvironmentalCompensationTool();

    @Test
    void appliesBaseTimesDaysTimesDeterrence() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                100_000L, Currency.INR, 10L, 2.5));

        assertThat(result.isComputed()).isTrue();
        // 100000 * 10 * 2.5 = 2,500,000 minor units
        assertThat(result.value()).contains(new Money(2_500_000L, Currency.INR));
    }

    @Test
    void unitDeterrenceLeavesBaseTimesDays() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                500L, Currency.INR, 7L, 1.0));

        assertThat(result.value()).contains(new Money(3_500L, Currency.INR));
    }

    @Test
    void zeroDaysYieldsZeroExposure() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                100L, Currency.INR, 0L, 3.0));

        assertThat(result.value()).contains(Money.zero(Currency.INR));
    }

    @Test
    void roundsHalfUpToWholeMinorUnits() {
        // 101 * 1 * 1.005 = 101.505 -> 102
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                101L, Currency.INR, 1L, 1.005));

        assertThat(result.value()).contains(new Money(102L, Currency.INR));
    }

    @Test
    void missingCurrencyIsUnavailable() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                100L, null, 1L, 1.0));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.value()).isEmpty();
        assertThat(result.reason()).isPresent();
    }

    @Test
    void negativeBaseRateIsUnavailable() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                -1L, Currency.INR, 1L, 1.0));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void negativeDaysIsUnavailable() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                100L, Currency.INR, -3L, 1.0));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void nonFiniteDeterrenceIsUnavailable() {
        ToolResult<Money> nan = tool.execute(new EnvironmentalCompensationRequest(
                100L, Currency.INR, 1L, Double.NaN));
        ToolResult<Money> infinite = tool.execute(new EnvironmentalCompensationRequest(
                100L, Currency.INR, 1L, Double.POSITIVE_INFINITY));

        assertThat(nan.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(infinite.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void overflowingProductIsUnavailable() {
        ToolResult<Money> result = tool.execute(new EnvironmentalCompensationRequest(
                Long.MAX_VALUE, Currency.INR, 2L, 2.0));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.reason()).isPresent();
    }

    @Test
    void nullRequestIsUnavailable() {
        assertThat(tool.execute(null).status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void declarationIdMatches() {
        assertThat(tool.id()).isEqualTo(EnvironmentalCompensationTool.TOOL_ID);
        assertThat(tool.declaration().id()).isEqualTo(EnvironmentalCompensationTool.TOOL_ID);
    }
}
