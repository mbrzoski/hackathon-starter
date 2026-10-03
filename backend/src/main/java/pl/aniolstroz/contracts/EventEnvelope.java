package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Message on /ws/events. The JSON {@code type} property selects the payload and is written and read by Jackson,
 * so it has no record component. Every event carries a mode (API-02).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EventEnvelope.TranscriptSegmentEvent.class, name = "transcript.segment"),
    @JsonSubTypes.Type(value = EventEnvelope.RiskUpdateEvent.class, name = "risk.update"),
    @JsonSubTypes.Type(value = EventEnvelope.AlertCreatedEvent.class, name = "alert.created"),
    @JsonSubTypes.Type(value = EventEnvelope.AlertDecisionEvent.class, name = "alert.decision"),
    @JsonSubTypes.Type(value = EventEnvelope.SystemStatusEvent.class, name = "system.status"),
    @JsonSubTypes.Type(value = EventEnvelope.CallStartedEvent.class, name = "call.started"),
    @JsonSubTypes.Type(value = EventEnvelope.CallEndedEvent.class, name = "call.ended"),
    @JsonSubTypes.Type(value = EventEnvelope.PhoneCallEvent.class, name = "phone.call")
})
public sealed interface EventEnvelope {

    Mode mode();

    Instant at();

    record TranscriptSegmentEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid TranscriptSegment payload) implements EventEnvelope {
    }

    record RiskUpdateEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid RiskUpdate payload) implements EventEnvelope {
    }

    record AlertCreatedEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid Alert payload) implements EventEnvelope {
    }

    record AlertDecisionEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid Decision payload) implements EventEnvelope {
    }

    record SystemStatusEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid SystemStatus payload) implements EventEnvelope {
    }

    record CallStartedEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid CallStarted payload) implements EventEnvelope {
    }

    record CallEndedEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid CallEnded payload) implements EventEnvelope {
    }

    record PhoneCallEvent(@NotNull Mode mode, @NotNull Instant at, @NotNull @Valid PhoneCall payload) implements EventEnvelope {
    }
}
