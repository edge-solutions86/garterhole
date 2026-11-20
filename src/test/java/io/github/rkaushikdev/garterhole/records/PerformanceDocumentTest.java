package io.github.rkaushikdev.garterhole.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Building a performance from a document, which is how the service, the HTTP
 * routes and the command line all rebuild one: from a request body, or from a
 * record read back out of the store.
 */
class PerformanceDocumentTest {

    private static JsonNode doc(String json) {
        return Json.parse(json.replace('\'', '"'));
    }

    private static final String BAND = "'band':['Alice','Bob','Carol','Dan','Erin','Frank']";

    @Test
    void readsEveryFieldAFormSends() {
        Performance rung = Performance.fromDocument(doc("{'tower':' St  Mary ','date':'2025-05-18',"
                + "'title':'Plain Bob Minor','changes':1260," + BAND + ",'conductor':'Alice',"
                + "'composer':'A Composer','duration':45,'footnotes':['First quarter for Bob.']}"));
        assertThat(rung.tower()).isEqualTo("St Mary");
        assertThat(rung.date()).isEqualTo(LocalDate.of(2025, 5, 18));
        assertThat(rung.conductor()).hasValue("Alice");
        assertThat(rung.composer()).hasValue("A Composer");
        assertThat(rung.duration()).hasValue(45);
        assertThat(rung.footnotes()).containsExactly("First quarter for Bob.");
    }

    @Test
    void readsBackARecordTheServiceStored() {
        Performance rung = Performance.fromDocument(doc("{'tower':'St Mary','date':'2025-05-18',"
                + "'title':'Plain Bob Minor','changes':1260," + BAND + ",'conductor':null,"
                + "'composer':null,'kind':'quarter peal'}"));
        assertThat(rung.conductor()).isEmpty();
        assertThat(rung.composer()).isEmpty();
        assertThat(rung.id()).isEqualTo("2025-05-18-st-mary-plain-bob-minor-1260");
    }

    @Test
    void refusesALengthThatIsNotAWholeNumber() {
        assertThatThrownBy(() -> Performance.fromDocument(
                        doc("{'tower':'St Mary','date':'2025-05-18','title':'Plain Bob Minor','changes':12.5," + BAND
                                + "}")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("12.5 is not a number of changes");
        assertThatThrownBy(() -> Performance.fromDocument(
                        doc("{'tower':'St Mary','date':'2025-05-18','title':'Plain Bob Minor'," + BAND + "}")))
                .hasMessage("undefined is not a number of changes");
    }

    @Test
    void refusesADurationThatIsNotWholeMinutes() {
        assertThatThrownBy(() -> Performance.fromDocument(doc("{'tower':'St Mary','date':'2025-05-18',"
                        + "'title':'Plain Bob Minor','changes':1260," + BAND + ",'duration':4.5}")))
                .hasMessage("4.5 is not a length of time in minutes");
    }

    @Test
    void refusesAMissingBandOrAConductorWhoIsNotAName() {
        assertThatThrownBy(() -> Performance.fromDocument(
                        doc("{'tower':'St Mary','date':'2025-05-18','title':'Plain Bob Minor','changes':1260}")))
                .hasMessage("a performance needs a band");
        assertThatThrownBy(() -> Performance.fromDocument(doc("{'tower':'St Mary','date':'2025-05-18',"
                        + "'title':'Plain Bob Minor','changes':1260," + BAND + ",'conductor':7}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the conductor must be a string");
    }
}
