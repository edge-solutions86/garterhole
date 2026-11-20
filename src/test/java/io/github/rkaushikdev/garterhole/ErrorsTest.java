package io.github.rkaushikdev.garterhole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ErrorsTest {

    @Nested
    class GarterholeExceptionTest {

        @Test
        void carriesADefaultCode() {
            GarterholeException error = new GarterholeException("something went wrong");
            assertThat(error.code()).isEqualTo("garterhole_error");
            assertThat(error.name()).isEqualTo("GarterholeException");
            assertThat(error.getMessage()).isEqualTo("something went wrong");
        }

        @Test
        void takesACodeAndDetails() {
            GarterholeException error = new GarterholeException("nope", "custom", Map.of("bell", 7));
            assertThat(error.code()).isEqualTo("custom");
            assertThat(error.details()).isEqualTo(Map.of("bell", 7));
        }

        @Test
        void freezesTheDetailsSoAHandlerCannotEditThem() {
            GarterholeException error = new GarterholeException("nope", "custom", Map.of("bell", 7));
            assertThatThrownBy(() -> error.details().put("bell", 8)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void keepsACauseWhenOneIsGiven() {
            RuntimeException cause = new RuntimeException("disk");
            GarterholeException error = new GarterholeException("wrapped", cause);
            assertThat(error.getCause()).isSameAs(cause);
        }

        @Test
        void serialisesToSomethingAnHttpLayerCanSend() {
            GarterholeException error = new GarterholeException("nope", "custom", Map.of("bell", 7));
            assertThat(error.toJson())
                    .containsExactly(
                            Map.entry("name", "GarterholeException"),
                            Map.entry("code", "custom"),
                            Map.entry("message", "nope"),
                            Map.entry("details", Map.of("bell", 7)));
        }

        @Test
        void isARealException() {
            assertThat(new GarterholeException("x")).isInstanceOf(RuntimeException.class);
            assertThat(new GarterholeException("x").getStackTrace()).isNotEmpty();
        }
    }

    @Nested
    class ValidationExceptionTest {

        @Test
        void collectsIssues() {
            ValidationException error = ValidationException.withIssues("two problems", List.of("a", "b"));
            assertThat(error.code()).isEqualTo("validation_failed");
            assertThat(error.issues()).containsExactly("a", "b");
            assertThat(error.toJson().get("issues")).isEqualTo(List.of("a", "b"));
        }

        @Test
        void defaultsToNoIssues() {
            assertThat(new ValidationException("one problem").issues()).isEmpty();
        }
    }

    @Nested
    class NotationExceptionTest {

        @Test
        void recordsWhereTheReaderGaveUp() {
            NotationException error = new NotationException("bad symbol", "12x4", 3);
            assertThat(error.code()).isEqualTo("notation_error");
            assertThat(error.position()).hasValue(3);
            assertThat(error.source()).hasValue("12x4");
            assertThat(error.details()).containsEntry("position", 3);
        }

        @Test
        void worksWithoutAPosition() {
            NotationException error = new NotationException("blank");
            assertThat(error.position()).isEmpty();
        }
    }

    @Nested
    class TheRemainingKinds {

        @Test
        void namesWhatWasNotFound() {
            NotFoundException error = new NotFoundException("method", "abc");
            assertThat(error.code()).isEqualTo("not_found");
            assertThat(error.getMessage()).isEqualTo("no method with id \"abc\"");
            assertThat(error.kind()).isEqualTo("method");
            assertThat(error.id()).isEqualTo("abc");
        }

        @Test
        void reportsAConflict() {
            assertThat(new ConflictException("already there").code()).isEqualTo("conflict");
        }

        @Test
        void reportsAStorageFailureAndKeepsTheCause() {
            RuntimeException cause = new RuntimeException("ENOENT");
            StorageException error = new StorageException("cannot read journal", cause);
            assertThat(error.code()).isEqualTo("storage_error");
            assertThat(error.getCause()).isSameAs(cause);
        }

        @Test
        void reportsAConfigurationFailure() {
            assertThat(new ConfigException("no port").code()).isEqualTo("config_error");
        }
    }

    @Nested
    class IsGarterholeException {

        @Test
        void recognisesEveryKindInTheTaxonomy() {
            List<RuntimeException> errors = List.of(
                    new GarterholeException("a"),
                    new ValidationException("b"),
                    new NotationException("c"),
                    new NotFoundException("method", "d"),
                    new ConflictException("e"),
                    new StorageException("f"),
                    new ConfigException("g"));
            for (RuntimeException error : errors) {
                assertThat(GarterholeException.isGarterholeException(error)).isTrue();
            }
        }

        @Test
        void doesNotClaimErrorsFromElsewhere() {
            assertThat(GarterholeException.isGarterholeException(new RuntimeException("plain")))
                    .isFalse();
            assertThat(GarterholeException.isGarterholeException(new IllegalArgumentException("guard")))
                    .isFalse();
            assertThat(GarterholeException.isGarterholeException(null)).isFalse();
            assertThat(GarterholeException.isGarterholeException("boom")).isFalse();
        }
    }
}
