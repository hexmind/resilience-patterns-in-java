package pl.ts;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import pl.ts.common.Tries;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
class CircuitBreakerTest {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerTest.class);

    private WireMockServer server;
    private BoatsApi boatsApi;
    private List<CircuitBreakerEvent> events;

    @BeforeEach
    void setUp(@WiremockResolver.Wiremock WireMockServer server, @WiremockUriResolver.WiremockUri String uri) {
        this.server = server;
        this.boatsApi = new ApiConfiguration(uri).boatsApi();
        this.events = new LinkedList<>();
    }

    @Test
    void circuitBreaker() throws InterruptedException {
        // given
        server.stubFor(get(anyUrl()).willReturn(serverError()));
        Duration waitDurationInOpenState = Duration.ofMillis(100);
        CircuitBreaker circuitBreaker = CircuitBreaker.of("boats", CircuitBreakerConfig.custom()
                .ringBufferSizeInClosedState(4)
                .ringBufferSizeInHalfOpenState(2)
                .waitDurationInOpenState(waitDurationInOpenState)
                .build());
        captureEvents(circuitBreaker.getEventPublisher());
        Supplier<List<Boat>> withCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, boatsApi::getBoats);

        // when
        Tries.repeat(10, withCircuitBreaker);
        Thread.sleep(waitDurationInOpenState.toMillis());
        Tries.repeat(4, withCircuitBreaker);

        // then
        assertThat(this.events)
                .extracting(CircuitBreakerEvent::getEventType)
                .containsExactly(
                        // try x10
                        CircuitBreakerEvent.Type.ERROR,
                        CircuitBreakerEvent.Type.ERROR,
                        CircuitBreakerEvent.Type.ERROR,
                        CircuitBreakerEvent.Type.ERROR, // ringBufferInClosedState is full
                        CircuitBreakerEvent.Type.STATE_TRANSITION, // from CLOSED to OPEN
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        // sleep waitDurationInOpenState
                        CircuitBreakerEvent.Type.STATE_TRANSITION, // from OPEN to HALF_OPEN
                        // try x4
                        CircuitBreakerEvent.Type.ERROR,
                        CircuitBreakerEvent.Type.ERROR, // ringBufferInHalfOpenState is full
                        CircuitBreakerEvent.Type.STATE_TRANSITION, // from HALF_OPEN to OPEN
                        CircuitBreakerEvent.Type.NOT_PERMITTED,
                        CircuitBreakerEvent.Type.NOT_PERMITTED
                );
        server.verify(6, getRequestedFor(urlEqualTo("/api/boats")));
    }

    private void captureEvents(CircuitBreaker.EventPublisher eventPublisher) {
        eventPublisher.onEvent(events::add);
        eventPublisher.onSuccess(e -> log.info(e.toString()));
        eventPublisher.onError(e -> log.error(e.toString()));
        eventPublisher.onCallNotPermitted(e -> log.warn(e.toString()));
        eventPublisher.onStateTransition(e -> log.info(e.toString()));
    }

}
