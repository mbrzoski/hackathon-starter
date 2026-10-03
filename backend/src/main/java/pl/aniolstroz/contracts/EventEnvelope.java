package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
    @JsonSubTypes.Type(value = EventEnvelope.CallEndedEvent.class, name = "call.ended")
})
public sealed interface EventEnvelope {

    Mode mode();

    Instant at();

    record TranscriptSegmentEvent(Mode mode, Instant at, TranscriptSegment payload) implements EventEnvelope {
    }

    record RiskUpdateEvent(Mode mode, Instant at, RiskUpdate payload) implements EventEnvelope {
    }

    record AlertCreatedEvent(Mode mode, Instant at, Alert payload) implements EventEnvelope {
    }

    record AlertDecisionEvent(Mode mode, Instant at, Decision payload) implements EventEnvelope {
    }

    record SystemStatusEvent(Mode mode, Instant at, SystemStatus payload) implements EventEnvelope {
    }

    record CallStartedEvent(Mode mode, Instant at, CallStarted payload) implements EventEnvelope {
    }

    record CallEndedEvent(Mode mode, Instant at, CallEnded payload) implements EventEnvelope {
    }
}
