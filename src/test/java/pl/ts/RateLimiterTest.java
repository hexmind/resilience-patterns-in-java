package pl.ts;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.vavr.control.Try;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterTest.class);

    @Test
    void overLimit() {
        RateLimiter rateLimiter = RateLimiter.of("name", RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(2))
                .limitForPeriod(1)
                .timeoutDuration(Duration.ZERO)
                .build());
        Runnable withRateLimiter = RateLimiter.decorateRunnable(rateLimiter, this::evacuate);

        Try first = Try.runRunnable(withRateLimiter);
        Try second = Try.runRunnable(withRateLimiter);

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getCause()).isInstanceOf(RequestNotPermitted.class);
    }

    @Test
    void timeout() {
        RateLimiter rateLimiter = RateLimiter.of("name", RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(2))
                .limitForPeriod(1)
                .timeoutDuration(Duration.ofSeconds(2))
                .build());
        Runnable withRateLimiter = RateLimiter.decorateRunnable(rateLimiter, this::evacuate);

        Try first = Try.runRunnable(withRateLimiter);
        Try second = Try.runRunnable(withRateLimiter);

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
    }

    private void evacuate() {
        log.info("evacuated");
    }
}
