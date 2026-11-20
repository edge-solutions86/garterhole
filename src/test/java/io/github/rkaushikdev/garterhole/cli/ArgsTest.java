package io.github.rkaushikdev.garterhole.cli;

import static io.github.rkaushikdev.garterhole.cli.Args.flagIsSet;
import static io.github.rkaushikdev.garterhole.cli.Args.parseArgs;
import static io.github.rkaushikdev.garterhole.cli.Args.phraseFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArgsTest {

    private static final Map<String, FlagSpec> KNOWN = FlagSpec.flags(
            "stage", FlagSpec.value("s"), "name", FlagSpec.VALUE, "notation", FlagSpec.VALUE, "quiet", FlagSpec.SWITCH);

    private static ParsedArgs known(String... argv) {
        return parseArgs(List.of(argv), KNOWN);
    }

    @Nested
    class ReadingACommand {

        @Test
        void takesTheWordsBeforeTheFirstFlagAsTheCommand() {
            assertThat(known("method", "add", "--name", "Plain").command()).containsExactly("method", "add");
        }

        @Test
        void givesAnEmptyCommandWhenThereIsNone() {
            assertThat(parseArgs(List.of()).command()).isEmpty();
        }

        @Test
        void putsWordsAfterAFlagInTheRest() {
            ParsedArgs parsed = known("prove", "--quiet", "extra");
            assertThat(parsed.command()).containsExactly("prove");
            assertThat(parsed.rest()).containsExactly("extra");
        }

        @Test
        void putsEverythingAfterABareDoubleDashInTheRest() {
            ParsedArgs parsed = known("run", "--", "--not-a-flag", "x");
            assertThat(parsed.rest()).containsExactly("--not-a-flag", "x");
        }

        @Test
        void refusesSomethingThatIsNotAList() {
            assertThatThrownBy(() -> parseArgs(null)).hasMessageContaining("must be a list of arguments");
        }
    }

    @Nested
    class ReadingFlags {

        @Test
        void readsAFlagAndItsValue() {
            assertThat(known("--stage", "8").flags()).isEqualTo(Map.of("stage", Optional.of("8")));
        }

        @Test
        void readsAFlagWrittenWithAnEqualsSign() {
            assertThat(known("--stage=8").flags()).isEqualTo(Map.of("stage", Optional.of("8")));
        }

        @Test
        void readsAShortName() {
            assertThat(known("-s", "8").flags()).isEqualTo(Map.of("stage", Optional.of("8")));
        }

        @Test
        void readsAFlagWithNoValueAsTrue() {
            assertThat(known("--quiet").flags()).isEqualTo(Map.of("quiet", Optional.empty()));
        }

        @Test
        void readsSeveralFlags() {
            ParsedArgs parsed = known("--name", "Plain", "--stage", "8", "--quiet");
            assertThat(parsed.flags())
                    .isEqualTo(
                            Map.of("name", Optional.of("Plain"), "stage", Optional.of("8"), "quiet", Optional.empty()));
        }

        @Test
        void refusesAFlagNobodyDeclared() {
            assertThatThrownBy(() -> known("--colour", "blue")).hasMessage("there is no flag called --colour");
        }

        @Test
        void refusesAFlagThatNeedsAValueAndHasNone() {
            assertThatThrownBy(() -> known("--stage")).hasMessage("--stage needs a value");
            assertThatThrownBy(() -> known("--stage", "--quiet")).isInstanceOf(ValidationException.class);
        }

        @Test
        void takesAnyFlagWhenNoneAreDeclared() {
            assertThat(parseArgs(List.of("--anything=1")).flags()).isEqualTo(Map.of("anything", Optional.of("1")));
        }

        @Test
        void keepsAValueThatLooksLikeANegativeNumber() {
            assertThat(known("--stage", "-1").valueOf("stage")).hasValue("-1");
        }
    }

    @Nested
    class RequireFlag {

        private ParsedArgs parsed() {
            return known("--name", "Plain", "--quiet");
        }

        @Test
        void givesTheValue() {
            assertThat(requireFlag(parsed(), "name")).isEqualTo("Plain");
        }

        @Test
        void fallsBackWhenThereIsOne() {
            assertThat(requireFlag(parsed(), "stage", "8")).isEqualTo("8");
        }

        @Test
        void complainsWhenThereIsNeither() {
            assertThatThrownBy(() -> requireFlag(parsed(), "stage")).hasMessage("this command needs --stage");
        }

        @Test
        void treatsAFlagWithNoValueAsMissing() {
            assertThatThrownBy(() -> requireFlag(parsed(), "quiet")).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class RequireNumber {

        @Test
        void readsAWholeNumber() {
            assertThat(requireNumber(known("--stage", "8"), "stage")).isEqualTo(8);
        }

        @Test
        void fallsBackWhenThereIsOne() {
            assertThat(requireNumber(known(), "stage", 6)).isEqualTo(6);
        }

        @Test
        void complainsAboutAValueThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> requireNumber(known("--stage", "eight"), "stage"))
                    .hasMessageContaining("--stage must be a whole number");
        }

        @Test
        void complainsWhenItIsMissingAndThereIsNoFallback() {
            assertThatThrownBy(() -> requireNumber(known(), "stage")).hasMessageContaining("needs --stage");
        }
    }

    @Nested
    class FlagIsSet {

        @Test
        void isTrueForAFlagWrittenOnItsOwn() {
            assertThat(flagIsSet(known("--quiet"), "quiet")).isTrue();
        }

        @Test
        void isTrueForAFlagWrittenAsTrue() {
            assertThat(flagIsSet(known("--quiet=true"), "quiet")).isTrue();
        }

        @Test
        void isFalseForOneThatWasNotGiven() {
            assertThat(flagIsSet(known(), "quiet")).isFalse();
        }
    }

    @Nested
    class PhraseFlag {

        @Test
        void joinsTheFlagValueWithTheWordsThatFollowedIt() {
            ParsedArgs parsed = parseArgs(List.of("show", "--title", "Cambridge", "Surprise", "Major"));
            assertThat(phraseFlag(parsed, "title")).isEqualTo("Cambridge Surprise Major");
        }

        @Test
        void takesAValueThatWasProperlyQuoted() {
            assertThat(phraseFlag(parseArgs(List.of("--title", "Plain Bob Major")), "title"))
                    .isEqualTo("Plain Bob Major");
        }

        @Test
        void takesTheLeftoversAloneWhenTheFlagIsMissing() {
            ParsedArgs parsed = new ParsedArgs(List.of(), Map.of(), List.of("Plain", "Bob", "Major"));
            assertThat(phraseFlag(parsed, "title")).isEqualTo("Plain Bob Major");
        }

        @Test
        void fallsBackWhenThereIsNothingAtAll() {
            assertThat(phraseFlag(parseArgs(List.of()), "title", "Unnamed")).isEqualTo("Unnamed");
        }

        @Test
        void complainsWhenThereIsNothingAndNoFallback() {
            assertThatThrownBy(() -> phraseFlag(parseArgs(List.of()), "title"))
                    .hasMessage("this command needs --title");
        }
    }
}
