package io.github.rkaushikdev.garterhole.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The benchmark is not a gate, but it is code, and code that only runs by hand
 * is code that stops running. These tests check that every benchmark still
 * does what it claims and gives the answer it should, which is what makes the
 * numbers it prints worth reading.
 */
class BenchTest {

    @Nested
    class Time {

        @Test
        void givesTheNameBackWithADuration() {
            Bench.Timing result = Bench.time("nothing", () -> 42);
            assertThat(result.name()).isEqualTo("nothing");
            assertThat(result.ms()).isGreaterThanOrEqualTo(0);
            assertThat(result.answer()).isEqualTo(42);
        }

        @Test
        void runsTheWorkMoreThanOnceAndKeepsTheBest() {
            int[] runs = {0};
            Bench.time("counting", () -> runs[0] += 1, 4);
            assertThat(runs[0]).isEqualTo(4);
        }

        @Test
        void runsItOnceWhenAskedTo() {
            int[] runs = {0};
            Bench.time("once", () -> runs[0] += 1, 1);
            assertThat(runs[0]).isEqualTo(1);
        }
    }

    @Nested
    class TheBenchmarks {

        @Test
        void allHaveANameAndSomethingToRun() {
            assertThat(Bench.BENCHMARKS).hasSizeGreaterThan(3);
            for (Bench.Benchmark benchmark : Bench.BENCHMARKS) {
                assertThat(benchmark.name()).isNotEmpty();
                assertThat(benchmark.run()).isNotNull();
            }
        }

        @Test
        void isFrozenSoNothingIsAddedByAccident() {
            assertThatThrownBy(() -> Bench.BENCHMARKS.add(new Bench.Benchmark("more", () -> 1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void givesTheAnswersTheyShould() {
            Map<String, Object> byName =
                    Bench.runBenchmarks().stream().collect(Collectors.toMap(Bench.Timing::name, Bench.Timing::answer));
            assertThat(byName.get("ring a plain course of cambridge")).isEqualTo(225);
            assertThat(byName.get("prove a plain course of cambridge")).isEqualTo(224);
            assertThat(byName.get("ring and prove three bobs home")).isEqualTo(336);
            assertThat(byName.get("search for touches of plain bob minor")).isEqualTo(8);
        }

        @Test
        void timesEveryOneOfThem() {
            List<Bench.Timing> results = Bench.runBenchmarks();
            assertThat(results).hasSameSizeAs(Bench.BENCHMARKS);
            for (Bench.Timing result : results) {
                assertThat(Double.isFinite(result.ms())).isTrue();
            }
            assertThat(Bench.lines(results)).hasSameSizeAs(results).allMatch(line -> line.contains("  "));
        }
    }
}
