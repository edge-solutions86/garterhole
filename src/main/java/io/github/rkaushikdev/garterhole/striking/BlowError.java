package io.github.rkaushikdev.garterhole.striking;

/**
 * How far one blow was from where it should have been.
 *
 * <p>The error is the time the blow actually struck minus the time the model
 * says it should have. Early is negative and late is positive.
 *
 * @param bell the bell that struck
 * @param rowIndex the row it struck in, counted from zero
 * @param place the place it struck in, counted from one
 * @param at when it struck, in milliseconds
 * @param expected when it should have struck
 * @param error {@code at - expected}
 */
public record BlowError(int bell, int rowIndex, int place, double at, double expected, double error) {}
