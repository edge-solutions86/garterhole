package io.github.rkaushikdev.garterhole.cli.commands;

import static io.github.rkaushikdev.garterhole.cli.Args.flagIsSet;
import static io.github.rkaushikdev.garterhole.cli.Args.requireFlag;
import static io.github.rkaushikdev.garterhole.cli.Args.requireNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.cli.CommandContext;
import io.github.rkaushikdev.garterhole.cli.FlagSpec;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.composition.Analysis;
import io.github.rkaushikdev.garterhole.composition.TouchAnalysis;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.notation.Expansion;
import io.github.rkaushikdev.garterhole.notation.NotationFormat;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proving a touch from the command line.
 *
 * <p>A conductor writes a calling on the back of an envelope and wants to know
 * two things before the band arrives: how long it is, and whether it is true.
 * This answers both, and it answers them by ringing the touch rather than by
 * looking anything up.
 *
 * <p>The method can come from the library by title, or straight off the
 * command line as notation, because a conductor trying a method the tower has
 * never rung should not have to file it first.
 */
public final class ProveCommands {

    /** The flags these commands take. */
    public static final Map<String, FlagSpec> PROVE_FLAGS = FlagSpec.flags(
            "title", FlagSpec.value("t"),
            "touch", FlagSpec.VALUE,
            "notation", FlagSpec.VALUE,
            "stage", FlagSpec.value("s"),
            "name", FlagSpec.value("n"),
            "lead", FlagSpec.SWITCH,
            "json", FlagSpec.SWITCH,
            "quiet", FlagSpec.SWITCH);

    private ProveCommands() {}

    /**
     * {@code prove --title "Plain Bob Major" --touch 3(H)}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void prove(ParsedArgs parsed, CommandContext context) {
        Method method = methodFrom(parsed, context.service());
        TouchAnalysis analysis = Analysis.analyseTouch(method, requireFlag(parsed, "touch"));
        ObjectNode answer = Json.object();
        answer.put("title", analysis.title());
        answer.put("length", analysis.length());
        answer.put("kind", analysis.kind().label());
        answer.put("comesRound", analysis.comesRound());
        answer.put("true", analysis.proof().isTrue());
        answer.put("summary", analysis.summary());
        answer.set("calls", analysis.callsJson());
        answer.set("parts", analysis.partsJson());
        Json.putNumber(answer, "musicScore", analysis.music().score());
        context.output().answer(answer, value -> Analysis.reportLines(analysis));
        if (!analysis.proof().isTrue()) {
            context.output().problem("this touch is false");
        }
    }

    /**
     * {@code lead --notation x18x18x18x18,12 --stage 8}
     *
     * @param parsed the command line
     * @param context the service and the output
     */
    public static void printLead(ParsedArgs parsed, CommandContext context) {
        Method method = methodFrom(parsed, context.service());
        RowBlock block = RowBlock.fromChanges(Row.rounds(method.stage()), method.changes());
        List<String> lines = NotationFormat.writeLeadLines(method.changes(), block.rows());
        ObjectNode answer = Json.object().put("title", method.name());
        block.rows().forEach(row -> answer.withArray("rows").add(row.toString()));
        context.output().answer(answer, value -> lines);
    }

    /**
     * The method a command is about: one built from notation given on the line,
     * or the one the library holds under the title.
     *
     * @param parsed the command line
     * @param service the library to look in
     * @return the method
     */
    public static Method methodFrom(ParsedArgs parsed, Service service) {
        Optional<String> notation = parsed.valueOf("notation");
        if (notation.isPresent()) {
            int stage = requireNumber(parsed, "stage");
            Expansion.expandNotation(notation.get(), stage);
            return Method.of(requireFlag(parsed, "name", "Unnamed"), stage, notation.get());
        }
        String title = MethodCommands.titleFrom(parsed);
        JsonNode document = service.getMethod(title);
        return Method.of(
                document.path("name").asText(),
                document.path("stage").asInt(),
                document.path("notation").asText());
    }

    /**
     * @param parsed the command line
     * @return true when the caller asked for the lead to be printed as well
     */
    public static boolean wantsLead(ParsedArgs parsed) {
        return flagIsSet(parsed, "lead");
    }
}
