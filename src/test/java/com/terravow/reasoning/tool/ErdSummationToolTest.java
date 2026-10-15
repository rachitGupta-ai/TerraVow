package com.terravow.reasoning.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.domain.Currency;
import com.terravow.domain.DeterminationStatus;
import com.terravow.domain.Money;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ErdSummationTool}: ERD equals the sum of available components only, and the
 * UNAVAILABLE-on-failure path (Req 2.2, 2.6).
 */
class ErdSummationToolTest {

    private final ErdSummationTool tool = new ErdSummationTool();

    @Test
    void sumsOnlyAvailableComponents() {
        ToolResult<Money> result = tool.execute(new ErdSummationRequest(Currency.INR, List.of(
                ErdComponent.available("ec-exposure", new Money(1_000L, Currency.INR)),
                ErdComponent.unavailable("escert-forgone", "rate not in corpus"),
                ErdComponent.available("excess-energy", new Money(2_500L, Currency.INR)))));

        assertThat(result.isComputed()).isTrue();
        assertThat(result.value()).contains(new Money(3_500L, Currency.INR));
    }

    @Test
    void emptyComponentListSumsToZero() {
        ToolResult<Money> result =
                tool.execute(new ErdSummationRequest(Currency.INR, List.of()));

        assertThat(result.value()).contains(Money.zero(Currency.INR));
    }

    @Test
    void allUnavailableComponentsSumToZero() {
        ToolResult<Money> result = tool.execute(new ErdSummationRequest(Currency.INR, List.of(
                ErdComponent.unavailable("ec-exposure", "no corpus figure"),
                ErdComponent.unavailable("escert-forgone", "no corpus figure"))));

        assertThat(result.value()).contains(Money.zero(Currency.INR));
    }

    @Test
    void currencyMismatchOnAvailableComponentIsUnavailable() {
        ToolResult<Money> result = tool.execute(new ErdSummationRequest(Currency.INR, List.of(
                ErdComponent.available("ec-exposure", new Money(1_000L, Currency.USD)))));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
        assertThat(result.reason()).isPresent();
    }

    @Test
    void missingCurrencyIsUnavailable() {
        ToolResult<Money> result = tool.execute(new ErdSummationRequest(null, List.of()));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void missingComponentsIsUnavailable() {
        ToolResult<Money> result = tool.execute(new ErdSummationRequest(Currency.INR, null));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void overflowingSumIsUnavailable() {
        ToolResult<Money> result = tool.execute(new ErdSummationRequest(Currency.INR, List.of(
                ErdComponent.available("a", new Money(Long.MAX_VALUE, Currency.INR)),
                ErdComponent.available("b", new Money(1L, Currency.INR)))));

        assertThat(result.status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }

    @Test
    void nullRequestIsUnavailable() {
        assertThat(tool.execute(null).status()).isEqualTo(DeterminationStatus.UNAVAILABLE);
    }
}
