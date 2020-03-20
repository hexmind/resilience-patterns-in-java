package pl.ts;

import feign.FeignException;
import feign.jackson.JacksonDecoder;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import io.vavr.control.Try;
import pl.ts.common.Tries;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
class FeignDecoratorsTest {

    private WireMockServer server;
    private BoatsApi boatsApi;
    private CircuitBreaker circuitBreaker;

    /**
     * Instead of `Feign.builder` in {@link ApiConfiguration#ApiConfiguration(String)}
     */
    private BoatsApi createBoatsBoatsApi(String uri) {
        this.circuitBreaker = CircuitBreaker.of("boats", CircuitBreakerConfig.custom()
                .ringBufferSizeInClosedState(4)
                .build());
        return Resilience4jFeign.builder(FeignDecorators.builder()
                .withCircuitBreaker(circuitBreaker)
                .withFallback(new BoatsApiFallback(), HasStatus.ANY_SERVER_ERROR::test)
                // or .withRateLimiter(rateLimiter)
                .build())
                .decoder(new JacksonDecoder())
                .target(BoatsApi.class, uri);
    }

    @BeforeEach
    void setUp(@WiremockResolver.Wiremock WireMockServer server, @WiremockUriResolver.WiremockUri String uri) {
        this.server = server;
        this.boatsApi = createBoatsBoatsApi(uri);
    }

    @Test
    void fallback() {
        server.stubFor(get(anyUrl()).willReturn(serverError()));

        Boat response = boatsApi.getBoat(-1L);

        assertThat(response.getName()).isEqualTo(BoatsApiFallback.BOAT_NAME);
        server.verify(getRequestedFor(urlEqualTo("/api/boats/-1")));
    }

    @Test
    void circuitBreaker() {
        server.stubFor(get(anyUrl()).willReturn(badRequest()));

        List<Try<List<Boat>>> responses = Tries.repeat(10, boatsApi::getBoats);

        assertThat(responses).extracting(Try::getCause)
                .hasOnlyElementsOfTypes(
                        FeignException.class,
                        CallNotPermittedException.class);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(4);
        assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(6);
        server.verify(4, getRequestedFor(urlEqualTo("/api/boats")));
    }

}
