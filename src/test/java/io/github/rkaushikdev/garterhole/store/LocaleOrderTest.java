package io.github.rkaushikdev.garterhole.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The order {@code localeCompare} gives, which is the root collation: spaces,
 * then punctuation, then digits, then letters, accents and case only breaking
 * ties.
 */
class LocaleOrderTest {

    private static List<String> sorted(String... names) {
        List<String> list = new ArrayList<>(List.of(names));
        list.sort(LocaleOrder.ORDER);
        return list;
    }

    @Test
    void putsSpacesAndHyphensBeforeLetters() {
        assertThat(sorted("stoke", "st-peter", "stmary", "st mary"))
                .containsExactly("st mary", "st-peter", "stmary", "stoke");
    }

    @Test
    void ignoresCaseUntilEverythingElseIsEqual() {
        assertThat(sorted("b", "A", "a", "B")).containsExactly("a", "A", "b", "B");
        assertThat(sorted("Ab", "aB", "ab")).containsExactly("ab", "aB", "Ab");
    }

    @Test
    void putsDigitsBeforeLetters() {
        assertThat(sorted("t10", "ta", "t1", "t2")).containsExactly("t1", "t10", "t2", "ta");
    }

    @Test
    void sortsAnAccentedLetterWithItsPlainOne() {
        assertThat(sorted("ezra", "école", "eclair")).containsExactly("eclair", "école", "ezra");
        assertThat(LocaleOrder.compare("e", "é")).isNegative();
    }

    @Test
    void callsTheSameStringEqual() {
        assertThat(LocaleOrder.compare("Plain Bob Major", "Plain Bob Major")).isZero();
    }
}
