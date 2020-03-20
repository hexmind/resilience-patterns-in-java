package pl.ts;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.vavr.collection.Stream;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BulkheadTest {

    private static final Logger log = LoggerFactory.getLogger(BulkheadTest.class);
    private static final int NUMBER_OF_PADDLES = 4;
    private static final int NUMBER_OF_PEOPLE = 20;
    private AtomicLong availablePaddles;

    @BeforeEach
    void setUp() {
        availablePaddles = new AtomicLong(NUMBER_OF_PADDLES);
    }

    private void paddleNow() {
        try {
            log.info("{} paddles left", availablePaddles.decrementAndGet());
            Thread.sleep(100);
        } catch (InterruptedException e) {
            log.error(e.getMessage().toUpperCase());
            Thread.currentThread().interrupt();
        } finally {
            availablePaddles.incrementAndGet();
        }
    }

    private <T> void everybodyInTheBoat(Runnable task) throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(NUMBER_OF_PEOPLE);
        List<Callable<Object>> tasks = Stream.continually(task)
                .map(Executors::callable)
                .take(NUMBER_OF_PEOPLE).toJavaList();
        service.invokeAll(tasks, 500, TimeUnit.MILLISECONDS);
    }

    @Deprecated
    @Test
    void unlimited() throws InterruptedException {
        everybodyInTheBoat(this::paddleNow);
    }

    @Deprecated
    @Test
    void synchronize() throws InterruptedException {
        everybodyInTheBoat(this::paddleNowSync);
    }

    synchronized private void paddleNowSync() {
        paddleNow();
    }

    @Test
    void threadPoolBulkhead() throws InterruptedException {
        ThreadPoolBulkhead threadPoolBulkhead = ThreadPoolBulkhead.of("threads", ThreadPoolBulkheadConfig.custom()
                .coreThreadPoolSize(NUMBER_OF_PADDLES)
                .maxThreadPoolSize(NUMBER_OF_PADDLES)
//                .queueCapacity(1)
                .build());

        Runnable withBulkhead = ThreadPoolBulkhead.decorateRunnable(threadPoolBulkhead, this::paddleNow);

        everybodyInTheBoat(withBulkhead);
    }

    @Test
    void bulkhead() throws InterruptedException {
        Bulkhead bulkhead = Bulkhead.of("semaphore", BulkheadConfig.custom()
                .maxConcurrentCalls(NUMBER_OF_PADDLES)
                .build());
        Runnable withBulkhead = Bulkhead.decorateRunnable(bulkhead, this::paddleNow);

        everybodyInTheBoat(withBulkhead);
    }
}
