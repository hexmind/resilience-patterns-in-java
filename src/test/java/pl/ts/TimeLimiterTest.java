package pl.ts;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.vavr.control.Try;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeLimiterTest {

    private TimeLimiter timeLimiter;

    @BeforeEach
    void setUp() {
        timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(100))
                .build());
    }

    @Test
    void less() {
        Callable<Integer> wait42 = TimeLimiter.decorateFutureSupplier(timeLimiter, () -> sleep(42));

        Try<Integer> dream = Try.ofCallable(wait42);

        assertThat(dream).contains(42);
    }

    @Test
    void more() {
        Callable<Integer> wait666 = TimeLimiter.decorateFutureSupplier(timeLimiter, () -> sleep(666));

        Try<Integer> dream = Try.ofCallable(wait666);

        assertThat(dream).isEmpty();
        assertThat(dream.getCause()).isInstanceOf(TimeoutException.class);
    }

    private Future<Integer> sleep(int millis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(millis);
                return millis;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        });
    }

}
