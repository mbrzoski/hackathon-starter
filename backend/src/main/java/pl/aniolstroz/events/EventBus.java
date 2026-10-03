package pl.aniolstroz.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
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
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;

/**
 * Fan-out of events to /ws/events clients (CC-02) and the state snapshot sent on connect (API-03).
 * The snapshot holds the latest status of each component, the active call and the last alert.
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
    private AlertCreatedEvent lastAlert;

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
            case CallStartedEvent e -> activeCall = e;
            case CallEndedEvent e -> {
                if (activeCall != null && activeCall.payload().callId().equals(e.payload().callId())) {
                    activeCall = null;
                }
            }
            case AlertCreatedEvent e -> lastAlert = e;
            default -> {
            }
        }
    }

    private List<EventEnvelope> snapshot() {
        List<EventEnvelope> events = new ArrayList<>(statuses.values());
        if (activeCall != null) {
            events.add(activeCall);
        }
        if (lastAlert != null) {
            events.add(lastAlert);
        }
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
