package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.commands.ProveCommands.PROVE_FLAGS;
import static io.github.rkaushikdev.garterhole.cli.commands.ProveCommands.methodFrom;
import static io.github.rkaushikdev.garterhole.cli.commands.ProveCommands.printLead;
import static io.github.rkaushikdev.garterhole.cli.commands.ProveCommands.prove;
import static io.github.rkaushikdev.garterhole.cli.commands.ProveCommands.wantsLead;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.Service;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProveCommandsTest {

    @TempDir
    Path directory;

    private Service service;

    @BeforeEach
    void fileAMethod() {
        service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
        service.addMethod(new Method.Definition("Plain", 8, "x18x18x18x18,12"));
    }

    private static ParsedArgs args(String line) {
        return Args.parseArgs(List.of(line.split(" ")), PROVE_FLAGS);
    }

    private CommandContext context(Output.Collected collected) {
        return new CommandContext(service, collected.output());
    }

    @Nested
    class Prove {

        @Test
        void ringsATouchOfAMethodInTheLibrary() {
            Output.Collected collected = Output.collecting();
            prove(args("--title Plain Bob Major --touch 3(H)"), context(collected));
            assertThat(collected.out().get(0)).isEqualTo("Plain Bob Major");
            assertThat(collected.out().get(1)).isEqualTo("336 changes, touch");
            assertThat(collected.out().get(2)).isEqualTo("true, 336 rows");
        }

        @Test
        void ringsATouchOfNotationGivenOnTheLine() {
            Output.Collected collected = Output.collecting();
            prove(args("--notation x1x1x1,2 --stage 6 --name Plain --touch 3(H)"), context(collected));
            assertThat(collected.out().get(1)).isEqualTo("180 changes, touch");
        }

        @Test
        void printsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            prove(args("--title Plain Bob Major --touch 3(H)"), context(collected));
            JsonNode answer = Json.parse(String.join("\n", collected.out()));
            assertThat(answer.get("length").asInt()).isEqualTo(336);
            assertThat(answer.get("true").asBoolean()).isTrue();
        }

        @Test
        void saysSoWhenTheTouchIsFalse() {
            Output.Collected collected = Output.collecting();
            service.addMethod(new Method.Definition("Plain", 6, "x1x1x1,2"));
            prove(args("--title Plain Bob Minor --touch 3(-..-..)"), context(collected));
            assertThat(collected.err()).contains("this touch is false");
        }

        @Test
        void complainsAboutAMethodItDoesNotHold() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> prove(args("--title Bristol Surprise Major --touch 3(H)"), context(collected)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void complainsWhenThereIsNoTouch() {
            Output.Collected collected = Output.collecting();
            assertThatThrownBy(() -> prove(args("--title Plain Bob Major"), context(collected)))
                    .hasMessageContaining("needs --touch");
        }
    }

    @Nested
    class PrintLead {

        @Test
        void printsALeadWithTheNotationBesideTheRows() {
            Output.Collected collected = Output.collecting();
            printLead(args("--title Plain Bob Major"), context(collected));
            assertThat(collected.out()).hasSize(17);
            assertThat(collected.out().get(0)).contains("12345678");
            assertThat(collected.out().get(1)).contains("21436587");
        }

        @Test
        void printsTheRowsAsJsonWhenThatWasAskedFor() {
            Output.Collected collected = Output.collecting(true, false);
            printLead(args("--title Plain Bob Major"), context(collected));
            assertThat(Json.parse(String.join("\n", collected.out())).get("rows"))
                    .hasSize(17);
        }
    }

    @Nested
    class MethodFrom {

        @Test
        void takesOneFromTheLibraryByTitle() {
            assertThat(methodFrom(args("--title Plain Bob Major"), service).stage())
                    .isEqualTo(8);
        }

        @Test
        void buildsOneFromNotation() {
            Method method = methodFrom(args("--notation x1x1x1,2 --stage 6 --name Plain"), service);
            assertThat(method.leadLength()).isEqualTo(12);
            assertThat(method.name()).isEqualTo("Plain");
        }

        @Test
        void namesAnUnnamedMethodSomethingRatherThanRefusing() {
            assertThat(methodFrom(args("--notation x1x1x1,2 --stage 6"), service)
                            .name())
                    .isEqualTo("Unnamed");
        }

        @Test
        void complainsAboutNotationWithNoStage() {
            assertThatThrownBy(() -> methodFrom(args("--notation x1x1x1,2"), service))
                    .hasMessageContaining("needs --stage");
        }

        @Test
        void complainsAboutNotationItCannotRead() {
            assertThatThrownBy(() -> methodFrom(args("--notation x1?x1 --stage 6"), service))
                    .hasMessageContaining("not part of place notation");
        }
    }

    @Nested
    class WantsLead {

        @Test
        void isTrueWhenTheFlagIsGiven() {
            assertThat(wantsLead(args("--lead --title Plain Bob Major"))).isTrue();
        }

        @Test
        void isFalseWhenItIsNot() {
            assertThat(wantsLead(args("--title Plain Bob Major"))).isFalse();
        }
    }
}
