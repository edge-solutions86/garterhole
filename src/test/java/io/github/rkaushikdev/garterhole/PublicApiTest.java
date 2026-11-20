package io.github.rkaushikdev.garterhole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.rkaushikdev.garterhole.composition.Prover;
import io.github.rkaushikdev.garterhole.composition.TouchExpander;
import io.github.rkaushikdev.garterhole.composition.TouchParser;
import io.github.rkaushikdev.garterhole.method.Classifier;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.striking.Strike;
import io.github.rkaushikdev.garterhole.striking.StrikingSession;
import io.github.rkaushikdev.garterhole.striking.TimingModel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The public surface.
 *
 * <p>This test exists to make a removal deliberate. Anything public here is
 * something somebody may have used, so dropping one should fail a test rather
 * than turning up as a broken build in a tower's own tooling.
 */
class PublicApiTest {

    private static final String ROOT = "io.github.rkaushikdev.garterhole.";

    /** Every type the library offers, as it was offered under its original names. */
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("Bell", "tower.Bell"),
            Map.entry("Call", "composition.Call"),
            Map.entry("CallRegistry", "composition.CallRegistry"),
            Map.entry("Change", "core.Change"),
            Map.entry("CompetencyRegister", "tower.CompetencyRegister"),
            Map.entry("Composition", "composition.Composition"),
            Map.entry("ConfigError", "ConfigException"),
            Map.entry("ConflictError", "ConflictException"),
            Map.entry("Diary", "tower.Diary"),
            Map.entry("GarterholeError", "GarterholeException"),
            Map.entry("Interval", "tower.Interval"),
            Map.entry("IntervalSet", "tower.IntervalSet"),
            Map.entry("Journal", "store.Journal"),
            Map.entry("Logger", "util.Logger"),
            Map.entry("Method", "method.Method"),
            Map.entry("MethodLibrary", "method.MethodLibrary"),
            Map.entry("NotFoundError", "NotFoundException"),
            Map.entry("NotationError", "NotationException"),
            Map.entry("Performance", "records.Performance"),
            Map.entry("PerformanceCollection", "records.PerformanceCollection"),
            Map.entry("Repository", "store.Repository"),
            Map.entry("Ringer", "tower.Ringer"),
            Map.entry("Roster", "tower.Roster"),
            Map.entry("Row", "core.Row"),
            Map.entry("RowBlock", "core.RowBlock"),
            Map.entry("Service", "Service"),
            Map.entry("Snapshot", "store.Snapshot"),
            Map.entry("StorageError", "StorageException"),
            Map.entry("StrikeStream", "striking.StrikeStream"),
            Map.entry("StrikingSession", "striking.StrikingSession"),
            Map.entry("TimingModel", "striking.TimingModel"),
            Map.entry("Tower", "tower.Tower"),
            Map.entry("TruthTracker", "composition.TruthTracker"),
            Map.entry("UnitOfWork", "store.UnitOfWork"),
            Map.entry("ValidationError", "ValidationException"));

    /** Every constant the library offers, as {@code Class#FIELD}. */
    private static final Map<String, String> CONSTANTS = Map.of(
            "MAX_STAGE", "core.Stage#MAX",
            "MIN_STAGE", "core.Stage#MIN",
            "PRODUCT", "Version#PRODUCT",
            "TREBLE", "core.Bells#TREBLE",
            "VERSION", "Version#VERSION");

    @Test
    void offersEverythingItSaysItDoes() throws ReflectiveOperationException {
        for (Map.Entry<String, String> type : TYPES.entrySet()) {
            Class<?> offered = Class.forName(ROOT + type.getValue());
            assertThat(Modifier.isPublic(offered.getModifiers()))
                    .as(type.getKey() + " is not public")
                    .isTrue();
        }
        for (Map.Entry<String, String> constant : CONSTANTS.entrySet()) {
            String[] parts = constant.getValue().split("#", -1);
            Field field = Class.forName(ROOT + parts[0]).getField(parts[1]);
            assertThat(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))
                    .as(constant.getKey() + " is not a constant")
                    .isTrue();
        }
    }

    @Test
    void offersTheVersion() {
        assertThat(Version.VERSION).matches("\\d+\\.\\d+\\.\\d+");
        assertThat(Version.buildLabel()).contains(Version.PRODUCT);
    }

    @Test
    void offersEnoughToRingATouchWithoutReachingInside() {
        Method bob = Method.of("Plain", 8, "x18x18x18x18,12");
        TouchExpander.Rung rung = TouchExpander.expandTouch(bob, TouchParser.parseTouch("3(H)"));
        assertThat(TouchExpander.touchLength(rung)).isEqualTo(336);
        assertThat(Prover.prove(rung.block()).isTrue()).isTrue();
    }

    @Test
    void offersEnoughToClassifyAMethodWithoutReachingInside() {
        Method cambridge = Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
        assertThat(Classifier.classify(cambridge).className()).isEqualTo("Surprise");
        assertThat(Title.of(cambridge)).isEqualTo("Cambridge Surprise Major");
    }

    @Test
    void offersEnoughToAnalyseStrikingWithoutReachingInside() {
        TimingModel model = TimingModel.of(4, 250);
        List<Strike> strikes = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < 4; rowIndex += 1) {
            for (int place = 1; place <= 4; place += 1) {
                strikes.add(new Strike(place, model.expectedAt(rowIndex, place)));
            }
        }
        StrikingSession session = StrikingSession.analyse(4, strikes);
        assertThat(Math.abs(session.accuracy().rms())).isLessThan(5e-7);
    }

    @Test
    void offersTheTwoSurfaces() {
        assertThatCode(() ->
                        Class.forName(ROOT + "http.App").getMethod("build", Class.forName(ROOT + "http.App$Options")))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(ROOT + "cli.Program")
                        .getMethod("run", List.class, Class.forName(ROOT + "cli.Program$Options")))
                .doesNotThrowAnyException();
    }

    @Test
    void offersNothingThatIsNotThere() throws ClassNotFoundException {
        List<String> declared = new ArrayList<>();
        Path sources = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(Files::isRegularFile)
                    .map(path -> sources.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".java") && !name.endsWith("package-info.java"))
                    .forEach(name -> declared.add(
                            name.substring(0, name.length() - ".java".length()).replace('/', '.')));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertThat(declared).hasSizeGreaterThan(100);
        for (String name : declared) {
            assertThat(Class.forName(name)).as(name).isNotNull();
        }
    }
}
