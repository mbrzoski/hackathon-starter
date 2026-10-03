package pl.aniolstroz.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.CallEnded;
import pl.aniolstroz.contracts.CallStarted;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.Mode;

/**
 * Rule 6: every screen shows the real mode. The heartbeat belongs to no call, so it must not announce the mode of the
 * application (SCRIPTED from the settings) while a LIVE call is running: the badge would jump between the two.
 */
class HeartbeatModeTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private EventBus bus;
    private HeartbeatService heartbeat;
    private final List<EventEnvelope> published = new ArrayList<>();

    @BeforeEach
    void setUp() {
        AppProperties properties = mock(AppProperties.class);
        when(properties.events()).thenReturn(new AppProperties.Events(List.of(), 1000, 1000, 1000));
        when(properties.mode()).thenReturn(Mode.SCRIPTED);
        // An EventBus that also remembers what it was given, so the test can read the heartbeat it published.
        bus = new EventBus(new ObjectMapper().registerModule(new JavaTimeModule()), properties) {
            @Override
            public void publish(EventEnvelope event) {
                published.add(event);
                super.publish(event);
            }
        };
        heartbeat = new HeartbeatService(bus, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Mode modeOfLastHeartbeat() {
        heartbeat.beat();
        EventEnvelope last = published.get(published.size() - 1);
        assertThat(last).isInstanceOf(SystemStatusEvent.class);
        return ((SystemStatusEvent) last).mode();
    }

    @Test
    void withoutACallTheHeartbeatCarriesTheModeOfTheApplication() {
        assertThat(modeOfLastHeartbeat()).isEqualTo(Mode.SCRIPTED);
    }

    @Test
    void duringALiveCallTheHeartbeatCarriesLive() {
        bus.publish(new CallStartedEvent(Mode.LIVE, NOW, new CallStarted("call-1")));

        assertThat(modeOfLastHeartbeat()).isEqualTo(Mode.LIVE);
    }

    @Test
    void afterTheCallEndedTheHeartbeatGoesBackToTheModeOfTheApplication() {
        bus.publish(new CallStartedEvent(Mode.LIVE, NOW, new CallStarted("call-1")));
        bus.publish(new CallEndedEvent(Mode.LIVE, NOW, new CallEnded("call-1", false)));

        assertThat(modeOfLastHeartbeat()).isEqualTo(Mode.SCRIPTED);
    }

    @Test
    void modeOrDefaultFollowsTheActiveCallWhateverTheFallbackIs() {
        assertThat(bus.modeOrDefault(Mode.MOCK)).isEqualTo(Mode.MOCK);

        bus.publish(new CallStartedEvent(Mode.REPLAY, NOW, new CallStarted("call-2")));

        assertThat(bus.modeOrDefault(Mode.MOCK)).isEqualTo(Mode.REPLAY);
    }
}
