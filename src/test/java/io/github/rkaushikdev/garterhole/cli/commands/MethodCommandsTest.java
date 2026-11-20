package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.commands.MethodCommands.METHOD_FLAGS;
import static io.github.rkaushikdev.garterhole.cli.commands.MethodCommands.addMethod;
import static io.github.rkaushikdev.garterhole.cli.commands.MethodCommands.listMethods;
import static io.github.rkaushikdev.garterhole.cli.commands.MethodCommands.removeMethod;
import static io.github.rkaushikdev.garterhole.cli.commands.MethodCommands.showMethod;
import static io.github.rkaushikdev.garterhole.cli.commands.MethodCommands.titleFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.Args;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.Output;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.Json;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MethodCommandsTest {

    @TempDir
    Path directory;

    private Service service;

    @BeforeEach
    void openTheStore() {
        service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
    }

    private static ParsedArgs args(String line) {
        return Args.parseArgs(List.of(line.split(" ")), METHOD_FLAGS);
    }

    private CommandContext context(Output.Collected collected) {
        return new CommandContext(service, collected.output());
    }

    @Nested
    class MethodAdd {

        @Test
        void filesAMethodAndPrintsWhatItWorkedOut() {
            Output.Collected collected = Output.collecting();
            addMethod(args("--name Plain --stage 8 --notation x18x18x18x18,12"), context(collected));
            assertThat(collected.err().get(0)).isEqualTo("filed as Plain Bob Major");
            assertThat(collected.out().get(0)).isEqualTo("Plain Bob Major");
            assertThat(String.join("\n", collected.out())).contains("lead      16 changes");
        }

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            addMethod(args("--name Plain --stage 8 --notation x18x18x18x18,12"), context(collected));
            assertThat(Json.parse(String.join("\n", collected.out()))
                            .get("title")
                            .asText())
                    .isEqualTo("Plain Bob Major");
        }

        @Test
        void complainsWhenAFlagIsMissing() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> addMethod(args("--name Plain --stage 8"), context(collected)))
                    .hasMessageContaining("needs --notation");
        }

        @Test
        void complainsAboutAStageThatIsNotANumber() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> addMethod(args("--name Plain --stage eight --notation x1x1"), context(collected)))
                    .hasMessageContaining("must be a whole number");
        }
    }

    @Nested
    class MethodList {

        @BeforeEach
        void fileThreeMethods() {
            service.addMethod(new Method.Definition("Plain", 8, "x18x18x18x18,12"));
            service.addMethod(new Method.Definition("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12"));
            service.addMethod(new Method.Definition("Plain", 6, "x1x1x1,2"));
        }

        @Test
        void printsATableWithAHeading() {
            Output.Collected collected = Output.collecting();
            listMethods(args("list"), context(collected));
            assertThat(collected.out().get(0)).contains("title");
            assertThat(collected.out()).hasSize(4);
        }

        @Test
        void filtersByStage() {
            Output.Collected collected = Output.collecting();
            listMethods(args("list --stage 6"), context(collected));
            assertThat(collected.out()).hasSize(2);
        }

        @Test
        void filtersByClass() {
            Output.Collected collected = Output.collecting();
            listMethods(args("list --class Surprise"), context(collected));
            assertThat(collected.out()).hasSize(2);
        }

        @Test
        void saysSoWhenTheLibraryIsEmpty() {
            Service empty = new Service(new Repository(new Journal(directory.resolve("other.jsonl"))));
            Output.Collected collected = Output.collecting();
            listMethods(args("list"), new CommandContext(empty, collected.output()));
            assertThat(collected.out()).isEmpty();
            assertThat(collected.err().get(0)).contains("nothing in the library yet");
        }

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            listMethods(args("list"), context(collected));
            assertThat(Json.parse(String.join("\n", collected.out()))).hasSize(3);
        }
    }

    @Nested
    class MethodShow {

        @BeforeEach
        void fileCambridge() {
            service.addMethod(new Method.Definition("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12"));
        }

        @Test
        void describesAMethod() {
            Output.Collected collected = Output.collecting();
            showMethod(args("show --title Cambridge Surprise Major"), context(collected));
            assertThat(collected.out().get(0)).isEqualTo("Cambridge Surprise Major");
            assertThat(String.join("\n", collected.out())).contains("class     Surprise");
        }

        @Test
        void takesTheTitleFromWhatIsLeftOnTheLine() {
            Output.Collected collected = Output.collecting();
            showMethod(
                    Args.parseArgs(List.of("show", "--json=false", "Cambridge", "Surprise", "Major")),
                    context(collected));
            assertThat(collected.out().get(0)).isEqualTo("Cambridge Surprise Major");
        }

        @Test
        void complainsAboutAMethodItDoesNotHold() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> showMethod(args("show --title Bristol Surprise Major"), context(collected)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void complainsWhenThereIsNoTitleAtAll() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> showMethod(args("show"), context(collected)))
                    .hasMessageContaining("needs --title");
        }
    }

    @Nested
    class MethodRemove {

        @BeforeEach
        void filePlainBobMajor() {
            service.addMethod(new Method.Definition("Plain", 8, "x18x18x18x18,12"));
        }

        @Test
        void removesOneAndSaysSo() {
            Output.Collected collected = Output.collecting();
            removeMethod(args("remove --title Plain Bob Major"), context(collected));
            assertThat(collected.err().get(0)).isEqualTo("removed Plain Bob Major");
            assertThat(service.listMethods()).isEmpty();
        }

        @Test
        void complainsAboutOneItDoesNotHold() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> removeMethod(args("remove --title Bristol Surprise Major"), context(collected)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TitleFrom {

        @Test
        void prefersTheFlag() {
            assertThat(titleFrom(args("show --title Plain Bob Major"))).isEqualTo("Plain Bob Major");
        }

        @Test
        void fallsBackToTheRestOfTheLine() {
            assertThat(titleFrom(new ParsedArgs(List.of(), Map.of(), List.of("Plain", "Bob", "Major"))))
                    .isEqualTo("Plain Bob Major");
        }

        @Test
        void complainsWhenThereIsNeither() {
            assertThatThrownBy(() -> titleFrom(new ParsedArgs(List.of(), Map.of(), List.of())))
                    .hasMessageContaining("needs --title");
        }
    }
}
