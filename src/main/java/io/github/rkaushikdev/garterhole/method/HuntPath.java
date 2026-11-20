package io.github.rkaushikdev.garterhole.method;

/**
 * The shape of a hunt bell's path through a lead, which decides most of a
 * method's class name.
 */
public enum HuntPath {
    /** Plain hunting: straight up to the back and straight down again. */
    PLAIN("plain"),
    /** Treble bob hunting: dodging in each pair of places on the way. */
    TREBLE_BOB("treble bob"),
    /** A path with a place made somewhere along it. */
    TREBLE_PLACE("treble place");

    private final String label;

    HuntPath(String label) {
        this.label = label;
    }

    /**
     * @return the name ringers give the path, for example {@code treble bob}
     */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
