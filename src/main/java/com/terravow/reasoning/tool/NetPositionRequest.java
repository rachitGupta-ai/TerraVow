package com.terravow.reasoning.tool;

import com.terravow.domain.Money;

/**
 * Input arguments for the net-position arithmetic tool (Req 6.5).
 *
 * <p>Net position equals {@code total incentive value + avoided Environmental_Compensation exposure
 * − penalty exposure} (Req 6.5). All three amounts are {@link Money}; the tool verifies they share
 * a single currency and the result is reported in that currency.
 *
 * <p>This record is a plain argument carrier; the tool ({@link NetPositionTool}) performs all
 * validation so invalid inputs surface as an unavailable result rather than a construction-time
 * exception.
 *
 * @param incentives the total incentive value; may be {@code null}, surfaced as unavailable
 * @param avoidedEc  the avoided Environmental_Compensation exposure; may be {@code null}
 * @param penalty    the penalty exposure; may be {@code null}
 *
 * @see NetPositionTool
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 6.5</a>
 */
public record NetPositionRequest(Money incentives, Money avoidedEc, Money penalty) {
}
