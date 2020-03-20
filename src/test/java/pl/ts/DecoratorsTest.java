package pl.ts;

import io.github.resilience4j.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import pl.ts.common.Tries;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
class DecoratorsTest {

    private static final String CACHE_KEY = "boats";
    private WireMockServer server;
    private BoatsApi boatsApi;
    private CacheManager cacheManager;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private Cache<String, List<Boat>> cache;

    @BeforeEach
    void setUp(@WiremockResolver.Wiremock WireMockServer server, @WiremockUriResolver.WiremockUri String uri) {
        this.server = server;
        this.boatsApi = new ApiConfiguration(uri).boatsApi();
        this.circuitBreaker = CircuitBreaker.ofDefaults("CircuitBreaker");
        this.retry = Retry.ofDefaults("Retry");
        this.cacheManager = Caching.getCachingProvider().getCacheManager();
        this.cache = Cache.of(cacheManager.createCache("boatsCache", new MutableConfiguration<>()));
    }

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    @Test
    @DisplayName("nested decorators")
    void onSuccessNested() {
        server.stubFor(get(anyUrl()).willReturn(ok().withBody("[]")));
        Supplier<List<Boat>> getBoats = () -> Cache.decorateSupplier(cache,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        Retry.decorateSupplier(retry, boatsApi::getBoats)))
                .apply(CACHE_KEY);

        Tries.repeat(2, getBoats);

        assertThat(cache.getMetrics().getNumberOfCacheMisses()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
        server.verify(1, getRequestedFor(urlEqualTo("/api/boats")));
    }

    @Test
    @DisplayName("explicit decorators")
    void onSuccessExplicit() {
        server.stubFor(get(anyUrl()).willReturn(ok().withBody("[]")));
        Supplier<List<Boat>> getBoats = () -> {
            Supplier<List<Boat>> apiCall = boatsApi::getBoats;
            Supplier<List<Boat>> withRetry = Retry.decorateSupplier(retry, apiCall);
            Supplier<List<Boat>> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, withRetry);
            Function<String, List<Boat>> readCache = Cache.decorateSupplier(cache, withCircuitBreaker);
            return readCache.apply(CACHE_KEY);
        };

        Tries.repeat(2, getBoats);

        assertThat(cache.getMetrics().getNumberOfCacheMisses()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
        server.verify(1, getRequestedFor(urlEqualTo("/api/boats")));
    }

    @Test
    @DisplayName("composed decorators")
    void onFailureComposed() {
        server.stubFor(get(anyUrl()).willReturn(serverError()));
        Supplier<List<Boat>> getBoats = () -> ((UnaryOperator<Supplier<List<Boat>>>)
                get -> Retry.decorateSupplier(retry, get))
                .andThen(get -> CircuitBreaker.decorateSupplier(circuitBreaker, get))
                .andThen(get -> Cache.decorateSupplier(cache, get))
                .andThen(cache -> cache.apply(CACHE_KEY))
                .apply(boatsApi::getBoats);

        Tries.repeat(2, getBoats);

        assertThat(cache.getMetrics().getNumberOfCacheMisses()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(2);
        server.verify(6, getRequestedFor(urlEqualTo("/api/boats")));
    }

    @Test
    @DisplayName("fluent decorators")
    void onFailureFluent() {
        server.stubFor(get(anyUrl()).willReturn(serverError()));
        Supplier<List<Boat>> getBoats = () -> Decorators.ofSupplier(boatsApi::getBoats)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .withCache(cache)
                .decorate()
                .apply(CACHE_KEY);

        Tries.repeat(2, getBoats);

        assertThat(cache.getMetrics().getNumberOfCacheMisses()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(2);
        server.verify(6, getRequestedFor(urlEqualTo("/api/boats")));
    }

}
