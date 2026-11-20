package io.github.rkaushikdev.garterhole.http.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Handler;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.FixedClock;
import io.github.rkaushikdev.garterhole.util.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateLimitTest {

    @Nested
    class Limiter {

        @Test
        void letsRequestsThroughUpToTheLimit() {
            RateLimiter limiter = new RateLimiter(3, new FixedClock(0));
            assertThat(limiter.offer("a").allowed()).isTrue();
            assertThat(limiter.offer("a").allowed()).isTrue();
            assertThat(limiter.offer("a").allowed()).isTrue();
            assertThat(limiter.offer("a").allowed()).isFalse();
        }

        @Test
        void countsDownWhatIsLeft() {
            RateLimiter limiter = new RateLimiter(3, new FixedClock(0));
            assertThat(limiter.offer("a").remaining()).isEqualTo(2);
            assertThat(limiter.offer("a").remaining()).isEqualTo(1);
        }

        @Test
        void keepsCallersApart() {
            RateLimiter limiter = new RateLimiter(1, new FixedClock(0));
            assertThat(limiter.offer("a").allowed()).isTrue();
            assertThat(limiter.offer("b").allowed()).isTrue();
            assertThat(limiter.offer("a").allowed()).isFalse();
        }

        @Test
        void letsRequestsThroughAgainOnceTheWindowHasPassed() {
            FixedClock clock = new FixedClock(0);
            RateLimiter limiter = new RateLimiter(1, 1000, clock);
            assertThat(limiter.offer("a").allowed()).isTrue();
            assertThat(limiter.offer("a").allowed()).isFalse();
            clock.advance(1001);
            assertThat(limiter.offer("a").allowed()).isTrue();
        }

        @Test
        void slidesRatherThanResettingOnABoundary() {
            FixedClock clock = new FixedClock(0);
            RateLimiter limiter = new RateLimiter(2, 1000, clock);
            clock.advance(900);
            assertThat(limiter.offer("a").allowed()).isTrue();
            clock.advance(99);
            assertThat(limiter.offer("a").allowed()).isTrue();
            clock.advance(2);
            assertThat(limiter.offer("a").allowed()).isFalse();
        }

        @Test
        void dropsARequestThatHasAgedOutOfTheWindow() {
            FixedClock clock = new FixedClock(0);
            RateLimiter limiter = new RateLimiter(2, 1000, clock);
            limiter.offer("a");
            clock.advance(900);
            limiter.offer("a");
            clock.advance(200);
            assertThat(limiter.offer("a").allowed()).isTrue();
        }

        @Test
        void saysHowLongToWait() {
            FixedClock clock = new FixedClock(0);
            RateLimiter limiter = new RateLimiter(1, 5000, clock);
            limiter.offer("a");
            clock.advance(1000);
            assertThat(limiter.offer("a").retryAfter()).isEqualTo(4);
        }

        @Test
        void forgetsCallersWhoHaveGoneAway() {
            FixedClock clock = new FixedClock(0);
            RateLimiter limiter = new RateLimiter(5, 1000, clock);
            limiter.offer("a");
            limiter.offer("b");
            assertThat(limiter.size()).isEqualTo(2);
            clock.advance(2000);
            assertThat(limiter.sweep()).isEqualTo(2);
            assertThat(limiter.size()).isZero();
        }

        @Test
        void keepsACallerWhoIsStillGoing() {
            FixedClock clock = new FixedClock(0);
            RateLimiter limiter = new RateLimiter(5, 1000, clock);
            limiter.offer("a");
            clock.advance(500);
            assertThat(limiter.sweep()).isZero();
        }

        @Test
        void refusesSettingsThatWouldLetNobodyIn() {
            assertThatThrownBy(() -> new RateLimiter(0, Clock.system())).hasMessageContaining("would let nobody in");
            assertThatThrownBy(() -> new RateLimiter(RateLimiter.DEFAULT_LIMIT, 0, Clock.system()))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void defaultsToAMinute() {
            assertThat(new RateLimiter().window()).isEqualTo(Clock.MINUTE);
        }
    }

    @Nested
    class CallerOf {

        @Test
        void usesTheForwardedAddressWhenThereIsOne() {
            Request request = Request.builder()
                    .url("/")
                    .header("x-forwarded-for", "10.0.0.1, 10.0.0.2")
                    .build();
            assertThat(RateLimiter.callerOf(request)).isEqualTo("10.0.0.1");
        }

        @Test
        void fallsBackToACallerHeader() {
            assertThat(RateLimiter.callerOf(
                            Request.builder().url("/").header("x-caller", "cli").build()))
                    .isEqualTo("cli");
        }

        @Test
        void callsAnUnidentifiedRequestAnonymous() {
            assertThat(RateLimiter.callerOf(Request.get("/"))).isEqualTo("anonymous");
        }
    }

    @Nested
    class WithRateLimit {

        private final Handler handler = request -> Response.json(Json.object().put("ok", true));

        private Request request() {
            return Request.builder().url("/").header("x-caller", "cli").build();
        }

        @Test
        void letsARequestThroughAndSaysWhatIsLeft() {
            Handler wrapped = RateLimiter.withRateLimit(handler, 2, new FixedClock(0));
            Response response = wrapped.handle(request());
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.headers().get("x-rate-limit-remaining")).isEqualTo("1");
        }

        @Test
        void refusesOneOverTheLimit() {
            Handler wrapped = RateLimiter.withRateLimit(handler, 1, new FixedClock(0));
            wrapped.handle(request());
            Response response = wrapped.handle(request());
            assertThat(response.status()).isEqualTo(429);
            assertThat(response.body().path("error").path("code").asText()).isEqualTo("too_many_requests");
            assertThat(response.headers()).containsKey("retry-after");
        }

        @Test
        void takesALimiterItIsGivenSoSeveralHandlersCanShareOne() {
            RateLimiter limiter = new RateLimiter(1, new FixedClock(0));
            Handler first = RateLimiter.withRateLimit(handler, limiter);
            Handler second = RateLimiter.withRateLimit(handler, limiter);
            assertThat(first.handle(request()).status()).isEqualTo(200);
            assertThat(second.handle(request()).status()).isEqualTo(429);
        }
    }
}
