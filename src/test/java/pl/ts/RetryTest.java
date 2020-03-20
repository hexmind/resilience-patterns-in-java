package pl.ts;

import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.control.Try;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
class RetryTest {

    private WireMockServer server;
    private BoatsApi boatsApi;
    private Retry retryOnServerError;

    @BeforeEach
    void setUp(@WiremockResolver.Wiremock WireMockServer server, @WiremockUriResolver.WiremockUri String uri) {
        this.server = server;
        this.boatsApi = new ApiConfiguration(uri).boatsApi();
        this.retryOnServerError = Retry.of("ServerError", RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .retryOnException(HasStatus.TOO_MANY_REQUESTS.or(HasStatus.ANY_SERVER_ERROR))
                .build());
        server.stubFor(get(urlMatching(".*500")).willReturn(serverError()));
        server.stubFor(get(urlMatching(".*400")).willReturn(badRequest()));
    }

    @Test
    void retry500() {
        Supplier<Boat> withRetry = Retry.decorateSupplier(
                retryOnServerError,
                () -> boatsApi.getBoat(500L)
        );

        Try.ofSupplier(withRetry);

        server.verify(3, getRequestedFor(urlEqualTo("/api/boats/500")));
    }


    @Test
    void retry400Ignore() {
        Supplier<Boat> withRetry = Retry.decorateSupplier(
                retryOnServerError,
                () -> boatsApi.getBoat(400L)
        );

        Try.ofSupplier(withRetry);

        server.verify(1, getRequestedFor(urlEqualTo("/api/boats/400")));
    }

    @Test
    void retryAndFallback() {
        Supplier<Boat> withRetry = Retry.decorateSupplier(
                retryOnServerError,
                () -> boatsApi.getBoat(500L)
        );

        Boat result = Try.ofSupplier(withRetry)
                .recover(e -> new Boat())
                .get();

        assertThat(result).isNotNull();
        server.verify(3, getRequestedFor(urlEqualTo("/api/boats/500")));
    }

}
