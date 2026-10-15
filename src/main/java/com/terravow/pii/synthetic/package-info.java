/**
 * Synthetic data grounding, synthetic-label propagation, and the advisory disclaimer (Req 13.1,
 * 13.2, 13.5, 13.6, 14.15).
 *
 * <p>Where real facility, machine, maintenance, or job/run data is unavailable, the
 * {@link com.terravow.pii.synthetic.SyntheticDataGenerator} produces records whose values are each
 * grounded inside a published {@link com.terravow.pii.synthetic.ParameterRange} and whose shape
 * mirrors a real instrument structure (Req 13.1, 14.15). Every produced
 * {@link com.terravow.pii.synthetic.SyntheticRecord} is labelled
 * {@link com.terravow.domain.SyntheticLabel#SYNTHETIC}.
 *
 * <p>{@link com.terravow.pii.synthetic.Labeled} is the propagation carrier: it pairs a value with
 * its label and every derivation combinator folds inputs' labels through
 * {@link com.terravow.domain.SyntheticLabel#combine}, so a value derived — directly or transitively
 * — from any synthetic input is itself synthetic (Req 13.2, 14.15).
 *
 * <p>{@link com.terravow.pii.synthetic.DecisionOrientedOutput} attaches the canonical
 * {@link com.terravow.pii.synthetic.AdvisoryDisclaimer} "not legal, tax, or engineering advice" to
 * every decision-oriented output on both surface and export, and carries the propagated synthetic
 * label forward (Req 13.5, 13.6).
 */
package com.terravow.pii.synthetic;
