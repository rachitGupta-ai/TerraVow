package com.terravow.reasoning.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NetPositionTool}: the identity {@code incentives + avoided EC − penalty}
 * and the UNAVAILABLE-on-failure path (Req 6.5).
 */
class NetPositionToolTest {

    private final NetPositionTool tool = new NetPositionTool();

    @Test
    void computesIncentivesPlusAvoidedEcMinusPenalty() {
        ToolResult<Money> result = tool.execute(new NetPositionRequest(
                new Money(5_000L, Currency.INR),
                new Money(3_000L, Currency.INR),
                new Money(2_000L, Currency.INR)));

        assertThat(result.isComputed()).isTrue();
        // 5000 + 3000 - 2000 = 6000
        assertThat(result.value()).contains(new Money(6_000L, Currency.INR));
    }

    @Test
    void netPositionCanBeNegative() {
        ToolResult<Money> result = tool.execute(new NetPositionRequest(
                new Money(1_000L, Currency.INR),
                new Money(500L, Currency.INR),
                new Money(4_000L, Currency.INR)));

        assertThat(result.value()).contains(new Money(-2_500L, Currency.INR));
    }

    @Test
    void zeroEverywhereIsZero() {
        ToolResult<Money> result = tool.execute(new NetPositionRequest(
                Money.zero(Currency.INR), Money.zero(Currency.INR), Money.zero(Currency.INR)));

        assertThat(result.value()).contains(Money.zero(Currency.INR));
    }

    @Test
    void missingAmountIsUnavailable() {
        ToolResult<Money> result = tool.execute(new NetPositionRequest(
                new Money(1_000L, Currency.INR), null, new Money(1L, Currency.INR)));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.reason()).isPresent();
    }

    @Test
    void currencyMismatchIsUnavailable() {
        ToolResult<Money> result = tool.execute(new NetPositionRequest(
                new Money(1_000L, Currency.INR),
                new Money(500L, Currency.USD),
                new Money(200L, Currency.INR)));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void overflowingArithmeticIsUnavailable() {
        ToolResult<Money> result = tool.execute(new NetPositionRequest(
                new Money(Long.MAX_VALUE, Currency.INR),
                new Money(1L, Currency.INR),
                Money.zero(Currency.INR)));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void nullRequestIsUnavailable() {
        assertThat(tool.execute(null).status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }
}
