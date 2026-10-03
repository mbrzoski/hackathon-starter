package pl.aniolstroz.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.AlertDecisionEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.PhoneCallEvent;
import pl.aniolstroz.contracts.EventEnvelope.RiskUpdateEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;

/**
 * Fan-out of events to /ws/events clients (CC-02) and the state snapshot sent on connect (API-03).
 * The snapshot holds the latest status of each component and, while a call is active, that call with its alerts,
 * last risk update and decisions. Nothing of a call stays in it after {@code call.ended}.
 * Event payloads are never logged: they can contain transcript text.
 */
@Service
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private record Subscriber(ClientRole role, WebSocketSession session) {
    }

    private final ObjectMapper mapper;
    private final AppProperties.Events config;

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<Component, SystemStatusEvent> statuses = new ConcurrentHashMap<>();
    /** Guards the snapshot fields and keeps "snapshot, then live events" ordered for a new client. */
    private final ReentrantLock lock = new ReentrantLock();
    private CallStartedEvent activeCall;
    /** The simulated phone call while it is on (demo); a new client must learn about it. */
    private PhoneCallEvent phoneCall;
    /** Alerts of the current call by id, oldest first. Dropped when their call ends (API-03). */
    private final Map<String, AlertCreatedEvent> alerts = new LinkedHashMap<>();
    private RiskUpdateEvent lastRisk;
    /** Decisions about the alerts in {@link #alerts}. */
    private final List<AlertDecisionEvent> decisions = new ArrayList<>();

    EventBus(ObjectMapper mapper, AppProperties properties) {
        this.mapper = mapper;
        this.config = properties.events();
    }

    /** Wraps the session, sends the snapshot and starts delivering live events to it. */
    void register(ClientRole role, WebSocketSession raw) {
        var session = new ConcurrentWebSocketSessionDecorator(
                raw, config.sendTimeLimitMs(), config.bufferSizeLimitBytes());
        var subscriber = new Subscriber(role, session);
        lock.lock();
        try {
            for (EventEnvelope event : snapshot()) {
                if (visibleTo(role, event) && !send(subscriber, serialize(event))) {
                    return;
                }
            }
            subscribers.put(raw.getId(), subscriber);
        } finally {
            lock.unlock();
        }
    }

    void unregister(String sessionId) {
        subscribers.remove(sessionId);
    }

    public void publish(EventEnvelope event) {
        String json = serialize(event);
        lock.lock();
        try {
            remember(event);
        } finally {
            lock.unlock();
        }
        for (Subscriber subscriber : List.copyOf(subscribers.values())) {
            if (visibleTo(subscriber.role(), event)) {
                send(subscriber, json);
            }
        }
    }

    /** Single place for per-role filtering. For now every role sees every event. */
    private boolean visibleTo(ClientRole role, EventEnvelope event) {
        return true;
    }

    private void remember(EventEnvelope event) {
        switch (event) {
            case SystemStatusEvent e -> statuses.put(e.payload().component(), e);
            case CallStartedEvent e -> {
                activeCall = e;
                clearCallData();
            }
            case CallEndedEvent e -> endCall(e.payload().callId());
            case PhoneCallEvent e -> phoneCall = e.payload().active() ? e : null;
            case RiskUpdateEvent e -> lastRisk = e;
            case AlertCreatedEvent e -> alerts.put(e.payload().alertId(), e);
            case AlertDecisionEvent e -> {
                // A decision about an alert that is no longer current (stored alert of a finished call) is not state.
                if (alerts.containsKey(e.payload().alertId())) {
                    decisions.add(e);
                }
            }
            default -> {
            }
        }
    }

    /** The call is over: its risk, alerts and decisions are no longer "current", so a refreshed UI does not show them. */
    private void endCall(String callId) {
        if (activeCall != null && activeCall.payload().callId().equals(callId)) {
            activeCall = null;
        }
        alerts.values().removeIf(a -> a.payload().callId().equals(callId));
        if (lastRisk != null && lastRisk.payload().callId().equals(callId)) {
            lastRisk = null;
        }
        decisions.removeIf(d -> !alerts.containsKey(d.payload().alertId()));
    }

    private void clearCallData() {
        alerts.clear();
        lastRisk = null;
        decisions.clear();
    }

    /** Latest statuses, the simulated phone call, the active call, then its alerts, last risk update and decisions, in the order they happened. */
    private List<EventEnvelope> snapshot() {
        List<EventEnvelope> events = new ArrayList<>(statuses.values());
        if (phoneCall != null) {
            events.add(phoneCall);
        }
        if (activeCall != null) {
            events.add(activeCall);
        }
        events.addAll(alerts.values());
        if (lastRisk != null) {
            events.add(lastRisk);
        }
        events.addAll(decisions);
        return events;
    }

    private String serialize(EventEnvelope event) {
        try {
            return mapper.writerFor(EventEnvelope.class).writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + event.getClass().getSimpleName(), e);
        }
    }

    private boolean send(Subscriber subscriber, String json) {
        try {
            subscriber.session().sendMessage(new TextMessage(json));
            return true;
        } catch (Exception e) {
            // A slow or broken client is dropped; the others are not affected. Log the type only.
            log.warn("Dropping /ws/events client {}: {}", subscriber.session().getId(), e.getClass().getSimpleName());
            subscribers.remove(subscriber.session().getId());
            try {
                subscriber.session().close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignored) {
                // already closed
            }
            return false;
        }
    }
}
