package io.github.rkaushikdev.garterhole.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MethodLibraryTest {

    private static Method cambridge() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Method yorkshire() {
        return Method.of("Yorkshire", 8, "x38x14x58x16x12x38x14x78,12");
    }

    private static Method bristol() {
        return Method.of("Bristol", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    }

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    @Nested
    class AddingMethods {

        private MethodLibrary library;

        @BeforeEach
        void openALibrary() {
            library = new MethodLibrary();
        }

        @Test
        void startsEmpty() {
            assertThat(library.size()).isZero();
            assertThat(library.titles()).isEmpty();
        }

        @Test
        void filesAMethodUnderTheTitleItsChangesGiveIt() {
            assertThat(library.add(cambridge())).isEqualTo("Cambridge Surprise Major");
            assertThat(library.size()).isEqualTo(1);
        }

        @Test
        void addsSeveralAtOnce() {
            assertThat(library.addAll(List.of(cambridge(), bristol(), plainBobMinor())))
                    .containsExactly("Cambridge Surprise Major", "Bristol Surprise Major", "Plain Bob Minor");
        }

        @Test
        void refusesASecondMethodWithTheSameTitle() {
            library.add(cambridge());
            assertThatThrownBy(() -> library.add(cambridge())).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> library.add(cambridge()))
                    .hasMessageContaining("already holds \"Cambridge Surprise Major\"");
        }

        @Test
        void refusesAMethodThatRingsTheSameRowsUnderAnotherName() {
            library.add(cambridge());
            assertThatThrownBy(() -> library.add(Method.of("Impostor", 8, "x38x14x1258x36x14x58x16x78,12")))
                    .hasMessageContaining("rings the same rows as \"Cambridge Surprise Major\"");
        }

        @Test
        void refusesAnythingThatIsNotAMethod() {
            assertThatThrownBy(() -> library.add(null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void stopsPartWayThroughABatchThatClashes() {
            assertThatThrownBy(() -> library.addAll(List.of(cambridge(), yorkshire(), cambridge())))
                    .isInstanceOf(ConflictException.class);
            assertThat(library.size()).isEqualTo(2);
        }
    }

    @Nested
    class ReadingTheLibrary {

        private MethodLibrary library;

        @BeforeEach
        void fillALibrary() {
            library = new MethodLibrary();
            library.addAll(List.of(cambridge(), yorkshire(), bristol(), plainBobMinor()));
        }

        @Test
        void saysWhetherItHoldsATitle() {
            assertThat(library.has("Cambridge Surprise Major")).isTrue();
            assertThat(library.has("Superlative Surprise Major")).isFalse();
        }

        @Test
        void getsAMethodByTitle() {
            assertThat(library.get("Bristol Surprise Major").name()).isEqualTo("Bristol");
        }

        @Test
        void throwsForATitleItDoesNotHold() {
            assertThatThrownBy(() -> library.get("Superlative Surprise Major")).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> library.get("Superlative Surprise Major"))
                    .hasMessageContaining("no method with id");
        }

        @Test
        void findsWithoutThrowing() {
            assertThat(library.find("Cambridge Surprise Major")).isPresent();
            assertThat(library.find("Superlative Surprise Major")).isEmpty();
        }

        @Test
        void findsAMethodByWhatItRings() {
            Method impostor = Method.of("Impostor", 8, "-38-14-1258-36-14-58-16-78,12");
            assertThat(library.findByNotation(impostor))
                    .get()
                    .extracting(Method::name)
                    .isEqualTo("Cambridge");
        }

        @Test
        void givesNothingWhenNothingRingsThoseRows() {
            assertThat(library.findByNotation(Method.of("Nothing", 6, "x16x16x16x16x16x16")))
                    .isEmpty();
        }

        @Test
        void listsTitlesInOrder() {
            assertThat(library.titles())
                    .containsExactly(
                            "Bristol Surprise Major",
                            "Cambridge Surprise Major",
                            "Plain Bob Minor",
                            "Yorkshire Surprise Major");
        }

        @Test
        void iteratesInTitleOrder() {
            List<String> names = new ArrayList<>();
            library.forEach(method -> names.add(method.name()));
            assertThat(names).containsExactly("Bristol", "Cambridge", "Plain", "Yorkshire");
        }
    }

    @Nested
    class RemovingMethods {

        @Test
        void takesAMethodOutAndFreesItsTitle() {
            MethodLibrary library = new MethodLibrary();
            library.add(cambridge());
            assertThat(library.remove("Cambridge Surprise Major").name()).isEqualTo("Cambridge");
            assertThat(library.size()).isZero();
            assertThatCode(() -> library.add(cambridge())).doesNotThrowAnyException();
        }

        @Test
        void freesTheNotationAsWell() {
            MethodLibrary library = new MethodLibrary();
            library.add(cambridge());
            library.remove("Cambridge Surprise Major");
            assertThatCode(() -> library.add(Method.of("Impostor", 8, "x38x14x1258x36x14x58x16x78,12")))
                    .doesNotThrowAnyException();
        }

        @Test
        void throwsForATitleItDoesNotHold() {
            assertThatThrownBy(() -> new MethodLibrary().remove("Nothing Major"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class SlugFor {

        @Test
        void makesATitleSafeForAUrl() {
            assertThat(MethodLibrary.slugFor("Cambridge Surprise Major")).isEqualTo("cambridge-surprise-major");
        }
    }
}
