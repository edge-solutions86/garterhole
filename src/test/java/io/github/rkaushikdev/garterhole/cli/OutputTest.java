package io.github.rkaushikdev.garterhole.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutputTest {

    @Nested
    class Answering {

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            collected.output().answer(Map.of("a", 1));
            assertThat(Json.parse(String.join("\n", collected.out()))).isEqualTo(Json.parse("{\"a\":1}"));
        }

        @Test
        void usesTheWriterItWasGivenWhenItIsNotPrintingJson() {
            Output.Collected collected = Output.collecting();
            collected.output().answer(Map.of("a", 1), value -> List.of("a is " + value.get("a")));
            assertThat(collected.out()).containsExactly("a is 1");
        }

        @Test
        void printsAStringAsItIsWhenThereIsNoWriter() {
            Output.Collected collected = Output.collecting();
            collected.output().answer("hello");
            assertThat(collected.out()).containsExactly("hello");
        }

        @Test
        void fallsBackToJsonOnOneLineForAValueWithNoWriter() {
            Output.Collected collected = Output.collecting();
            collected.output().answer(Map.of("a", 1));
            assertThat(collected.out()).containsExactly("{\"a\":1}");
        }

        @Test
        void ignoresTheWriterWhenPrintingJson() {
            Output.Collected collected = Output.collecting(true, false);
            collected.output().answer(Map.of("a", 1), value -> List.of("never printed"));
            assertThat(String.join("", collected.out())).contains("\"a\": 1");
        }
    }

    @Nested
    class NotesAndProblems {

        @Test
        void sendsANoteToTheErrorStream() {
            Output.Collected collected = Output.collecting();
            collected.output().note("filed");
            assertThat(collected.err()).containsExactly("filed");
            assertThat(collected.out()).isEmpty();
        }

        @Test
        void leavesNotesOutOfAQuietRun() {
            Output.Collected collected = Output.collecting(false, true);
            collected.output().note("filed");
            assertThat(collected.err()).isEmpty();
        }

        @Test
        void printsAProblemHoweverQuietTheRunIs() {
            Output.Collected collected = Output.collecting(false, true);
            collected.output().problem("that did not work");
            assertThat(collected.err()).containsExactly("that did not work");
        }
    }

    @Nested
    class Tables {

        @Test
        void linesTheColumnsUp() {
            Output.Collected collected = Output.collecting();
            collected.output().table(List.of(List.of("title", "stage"), List.of("Plain Bob Major", "8")));
            assertThat(collected.out().get(0)).isEqualTo("title            stage");
        }

        @Test
        void printsTheRowsAsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            collected.output().table(List.of(List.of("a", "b")));
            assertThat(Json.parse(collected.out().get(0))).isEqualTo(Json.parse("[[\"a\",\"b\"]]"));
        }

        @Test
        void rightAlignsTheColumnsItIsToldTo() {
            Output.Collected collected = Output.collecting();
            collected
                    .output()
                    .table(List.of(List.of("a", "1"), List.of("bb", "22")), List.of(Text.Align.LEFT, Text.Align.RIGHT));
            assertThat(collected.out().get(1)).isEqualTo("bb  22");
        }

        @Test
        void refusesSomethingThatIsNotRows() {
            assertThatThrownBy(() -> Output.collecting().output().table(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TheSinks {

        @Test
        void writesNowhereByDefault() {
            assertThatCode(() -> new Output().answer("hello")).doesNotThrowAnyException();
        }

        @Test
        void writesToTheStreamsItIsGiven() {
            ByteArrayOutputStream written = new ByteArrayOutputStream();
            PrintStream stream = new PrintStream(written, true, StandardCharsets.UTF_8);
            Output output = Output.console(stream, stream, false, false);
            output.answer("hello");
            output.note("a note");
            assertThat(written.toString(StandardCharsets.UTF_8)).isEqualTo("hello\na note\n");
        }
    }
}
