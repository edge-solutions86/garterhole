package io.github.rkaushikdev.garterhole.striking;

/**
 * One blow, as a recorder in the tower hears it: which bell struck, and when.
 *
 * <p>A strike on its own is not checked against anything, because whether the
 * bell exists depends on the stage of the stream it belongs to.
 * {@link StrikeStream} does that checking.
 *
 * @param bell the bell that struck, the treble being 1
 * @param at milliseconds from the start of the recording
 */
public record Strike(int bell, double at) {}
