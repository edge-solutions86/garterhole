package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.Args.phraseFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireNumber;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.FlagSpec;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * The method commands.
 *
 * <p>{@code method add} is the one that earns its keep. A tower librarian types
 * the name and the notation off a printed collection, and the class, the lead
 * head and the lead length are worked out rather than copied, which is where
 * the mistakes in a hand kept library come from.
 */
public final class MethodCommands {

    /** The flags these commands take. */
    public static final Map<String, FlagSpec> METHOD_FLAGS = FlagSpec.flags(
            "name", FlagSpec.value("n"),
            "stage", FlagSpec.value("s"),
            "notation", FlagSpec.VALUE,
            "title", FlagSpec.value("t"),
            "class", FlagSpec.VALUE,
            "json", FlagSpec.SWITCH,
            "quiet", FlagSpec.SWITCH);

    private MethodCommands() {}

    /**
     * {@code method add --name Plain --stage 8 --notation x18x18x18x18,12}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void addMethod(ParsedArgs parsed, CommandContext context) {
        String name = requireFlag(parsed, "name");
        int stage = requireNumber(parsed, "stage");
        String notation = requireFlag(parsed, "notation");
        JsonNode document = context.service().addMethod(new Method.Definition(name, stage, notation));
        context.output().note("filed as " + document.path("title").asText());
        context.output().answer(document, MethodCommands::describeMethod);
    }

    /**
     * {@code method list [--stage 8] [--class Surprise]}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void listMethods(ParsedArgs parsed, CommandContext context) {
        Optional<String> wantedStage = parsed.asText("stage");
        Optional<String> wantedClass = parsed.asText("class");
        List<JsonNode> methods = context.service().listMethods().stream()
                .filter(entry -> wantedStage.isEmpty()
                        || Json.templateText(entry.path("stage")).equals(wantedStage.get()))
                .filter(entry -> wantedClass.isEmpty()
                        || (entry.path("class").isTextual()
                                && entry.path("class").textValue().equals(wantedClass.get())))
                .toList();
        if (context.output().json()) {
            context.output().answer(methods);
            return;
        }
        if (methods.isEmpty()) {
            context.output().note("nothing in the library yet");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("title", "lead", "lead head", "notation"));
        for (JsonNode entry : methods) {
            rows.add(List.of(
                    entry.path("title").asText(),
                    Json.templateText(entry.path("leadLength")),
                    entry.path("leadHead").asText(),
                    entry.path("notation").asText()));
        }
        context.output().table(rows, List.of(Text.Align.LEFT, Text.Align.RIGHT, Text.Align.LEFT, Text.Align.LEFT));
    }

    /**
     * {@code method show --title "Cambridge Surprise Major"}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void showMethod(ParsedArgs parsed, CommandContext context) {
        String title = titleFrom(parsed);
        context.output().answer(context.service().describeMethod(title), MethodCommands::describeMethod);
    }

    /**
     * {@code method remove --title "Cambridge Surprise Major"}
     *
     * @param parsed the command line
     * @param context the service and the output
     * @throws ValidationException when the library does not hold the method
     */
    public static void removeMethod(ParsedArgs parsed, CommandContext context) {
        String title = titleFrom(parsed);
        if (!context.service().removeMethod(title)) {
            throw new ValidationException("the library does not hold " + Json.quote(title));
        }
        context.output().note("removed " + title);
        context.output().answer(Json.object().put("removed", title));
    }

    /**
     * The title of the method a command is about, which may be written
     * unquoted across several words.
     *
     * @param parsed the command line
     * @return the title
     * @throws ValidationException when there is no title at all
     */
    public static String titleFrom(ParsedArgs parsed) {
        return phraseFlag(parsed, "title");
    }

    private static List<String> describeMethod(JsonNode method) {
        List<String> lines = new ArrayList<>();
        lines.add(method.path("title").asText());
        lines.add("notation  " + Json.templateText(method.path("notation")));
        lines.add("lead      " + Json.templateText(method.path("leadLength")) + " changes, lead head "
                + Json.templateText(method.path("leadHead")));
        if (!Json.isUndefined(method.path("className"))) {
            lines.add("class     " + Json.templateText(method.path("className")));
        }
        JsonNode huntBells = method.path("huntBells");
        if (!Json.isUndefined(huntBells)) {
            lines.add("hunt      "
                    + StreamSupport.stream(huntBells.spliterator(), false)
                            .map(Json::templateText)
                            .collect(Collectors.joining(", ")));
        }
        return lines;
    }
}
