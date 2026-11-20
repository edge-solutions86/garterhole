package io.github.rkaushikdev.garterhole.tools;

import io.github.rkaushikdev.garterhole.composition.Falseness;
import io.github.rkaushikdev.garterhole.composition.Prover;
import io.github.rkaushikdev.garterhole.composition.TouchExpander;
import io.github.rkaushikdev.garterhole.composition.TouchParser;
import io.github.rkaushikdev.garterhole.composition.TouchSearch;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.List;
import java.util.function.Supplier;

/**
 * How long the slow parts take. Run with {@code ./gradlew bench}.
 *
 * <p>Three things in this library are slow enough to matter, and they are slow
 * for different reasons: proving a long touch is bounded by the number of rows,
 * finding the false course heads is bounded by the square of the course length,
 * and the touch search is bounded by the branching. A change that makes any of
 * them worse should be noticed by somebody, and a number printed at the end of
 * a run is how.
 *
 * <p>This is not a regression gate. It prints and it exits zero, because a
 * timing that fails a build on a busy machine teaches everybody to ignore it.
 */
public final class Bench {

    private static final Method CAMBRIDGE = Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    private static final Method PLAIN_BOB_MINOR = Method.of("Plain", 6, "x1x1x1,2");
    private static final Method PLAIN_BOB_MAJOR = Method.of("Plain", 8, "x18x18x18x18,12");
    private static final int DEFAULT_RUNS = 3;
    private static final double NANOS_PER_MILLI = 1e6;

    /** Everything that gets timed. */
    public static final List<Benchmark> BENCHMARKS = List.of(
            new Benchmark("ring a plain course of cambridge", () -> PlainCourse.plainCourse(CAMBRIDGE)
                    .length()),
            new Benchmark("prove a plain course of cambridge", () -> Prover.prove(PlainCourse.plainCourse(CAMBRIDGE))
                    .distinct()),
            new Benchmark("ring and prove three bobs home", () -> Prover.prove(
                            TouchExpander.expandTouch(PLAIN_BOB_MAJOR, TouchParser.parseTouch("3(H)"))
                                    .block())
                    .rows()),
            new Benchmark("false course heads of cambridge", () -> Falseness.falseCourseHeads(CAMBRIDGE)
                    .size()),
            new Benchmark("search for touches of plain bob minor", () -> TouchSearch.searchTouches(
                            TouchSearch.Request.of(PLAIN_BOB_MINOR)
                                    .withMaxLeads(8)
                                    .withLimit(8))
                    .size()));

    private Bench() {}

    /**
     * One thing that gets timed.
     *
     * @param name what it is, as printed
     * @param run the work, which gives back an answer worth checking
     */
    public record Benchmark(String name, Supplier<?> run) {}

    /**
     * How long something took.
     *
     * @param name what it was
     * @param ms the best of its runs, in milliseconds
     * @param answer what the last run gave back
     */
    public record Timing(String name, double ms, Object answer) {}

    /**
     * Time something, running it three times and keeping the best.
     *
     * @param name what it is
     * @param work the work
     * @return the best time and the answer
     */
    public static Timing time(String name, Supplier<?> work) {
        return time(name, work, DEFAULT_RUNS);
    }

    /**
     * Time something, running it a few times and keeping the best.
     *
     * @param name what it is
     * @param work the work
     * @param runs how many times to run it
     * @return the best time and the answer
     */
    public static Timing time(String name, Supplier<?> work, int runs) {
        double best = Double.POSITIVE_INFINITY;
        Object answer = null;
        for (int run = 0; run < runs; run += 1) {
            long started = System.nanoTime();
            answer = work.get();
            best = Math.min(best, (System.nanoTime() - started) / NANOS_PER_MILLI);
        }
        return new Timing(name, best, answer);
    }

    /**
     * @return every benchmark, timed
     */
    public static List<Timing> runBenchmarks() {
        return BENCHMARKS.stream()
                .map(benchmark -> time(benchmark.name(), benchmark.run()))
                .toList();
    }

    /**
     * @param results the timings
     * @return one line per timing, the names lined up
     */
    public static List<String> lines(List<Timing> results) {
        int width = results.stream()
                .mapToInt(result -> result.name().length())
                .max()
                .orElse(0);
        return results.stream()
                .map(result -> Text.padRight(result.name(), width) + "  "
                        + Text.padLeft(Clock.describeDuration(result.ms()), 8) + "  " + result.answer())
                .toList();
    }

    /**
     * Run every benchmark and print the timings.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        lines(runBenchmarks()).forEach(System.out::println);
    }
}
