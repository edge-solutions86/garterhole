package io.github.rkaushikdev.garterhole;

/**
 * The published version of the library. Kept in a class of its own so that the
 * command line banner and the HTTP health route read the same value, and so
 * that the release check has one place in the code to compare the build file
 * and the changelog against.
 */
public final class Version {

    /** The version, which the build file and the changelog must agree with. */
    public static final String VERSION = "1.0.0";

    /** The name used in log lines, the command line banner and the HTTP server header. */
    public static final String PRODUCT = "garterhole";

    private Version() {}

    /**
     * @return a short human readable build label, for example {@code garterhole/1.0.0}
     */
    public static String buildLabel() {
        return PRODUCT + "/" + VERSION;
    }
}
