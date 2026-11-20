package io.github.rkaushikdev.garterhole.http;

import static io.github.rkaushikdev.garterhole.http.Documents.doc;
import static io.github.rkaushikdev.garterhole.http.Pagination.pageOf;
import static io.github.rkaushikdev.garterhole.http.Pagination.pageOut;
import static io.github.rkaushikdev.garterhole.http.Pagination.paginate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Pagination.Page;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaginationTest {

    private static Request ask(String query) {
        return Request.get("/things" + query);
    }

    private static List<Integer> things(int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    @Nested
    class PageOf {

        @Test
        void usesTheDefaultsWhenNothingIsAskedFor() {
            assertThat(pageOf(ask(""))).isEqualTo(new Page(Pagination.DEFAULT_LIMIT, 0));
        }

        @Test
        void readsALimitAndAnOffset() {
            assertThat(pageOf(ask("?limit=10&offset=20"))).isEqualTo(new Page(10, 20));
        }

        @Test
        void refusesALimitThatWouldReturnNothing() {
            assertThatThrownBy(() -> pageOf(ask("?limit=0"))).hasMessageContaining("would return nothing");
        }

        @Test
        void refusesALimitLargerThanTheCeiling() {
            assertThatThrownBy(() -> pageOf(ask("?limit=" + (Pagination.MAX_LIMIT + 1))))
                    .hasMessageContaining("more than the 500 allowed");
        }

        @Test
        void refusesAnOffsetBeforeTheStart() {
            assertThatThrownBy(() -> pageOf(ask("?offset=-1"))).hasMessageContaining("before the start of the list");
        }

        @Test
        void refusesALimitThatIsNotANumber() {
            assertThatThrownBy(() -> pageOf(ask("?limit=lots"))).isInstanceOf(ValidationException.class);
        }

        @Test
        void takesDifferentBounds() {
            assertThat(pageOf(ask(""), 5, Pagination.MAX_LIMIT).limit()).isEqualTo(5);
            assertThatThrownBy(() -> pageOf(ask("?limit=10"), Pagination.DEFAULT_LIMIT, 5))
                    .hasMessageContaining("the 5 allowed");
        }
    }

    @Nested
    class PageOut {

        @Test
        void cutsThePageOutAndDescribesIt() {
            ObjectNode answer = pageOut(things(10), new Page(3, 0), "things");
            assertThat(answer.get("things")).isEqualTo(doc("[0,1,2]"));
            assertThat(answer.get("total").asInt()).isEqualTo(10);
            assertThat(answer.get("returned").asInt()).isEqualTo(3);
            assertThat(answer.get("more").asBoolean()).isTrue();
        }

        @Test
        void saysThereIsNoMoreAtTheEnd() {
            ObjectNode answer = pageOut(things(10), new Page(3, 8), "things");
            assertThat(answer.get("things")).isEqualTo(doc("[8,9]"));
            assertThat(answer.get("more").asBoolean()).isFalse();
        }

        @Test
        void handlesAnOffsetPastTheEnd() {
            ObjectNode answer = pageOut(things(3), new Page(3, 10), "things");
            assertThat(answer.get("things")).isEmpty();
            assertThat(answer.get("more").asBoolean()).isFalse();
        }

        @Test
        void handlesAnEmptyList() {
            ObjectNode answer = pageOut(List.of(), new Page(10, 0), "things");
            assertThat(answer.get("total").asInt()).isZero();
            assertThat(answer.get("more").asBoolean()).isFalse();
        }

        @Test
        void namesTheListTheWayTheCallerAsked() {
            assertThat(pageOut(things(1), new Page(1, 0), "methods").get("methods"))
                    .isEqualTo(doc("[0]"));
        }
    }

    @Nested
    class Paginate {

        @Test
        void readsThePageAndCutsItInOneStep() {
            ObjectNode answer = paginate(ask("?limit=2&offset=1"), things(5), "things");
            assertThat(answer.get("things")).isEqualTo(doc("[1,2]"));
            assertThat(answer.get("offset").asInt()).isEqualTo(1);
        }

        @Test
        void passesABadPageOnAsARefusal() {
            assertThatThrownBy(() -> paginate(ask("?limit=0"), things(5), "things"))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
