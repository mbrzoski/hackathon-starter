package pl.aniolstroz.events;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/** /ws/events?role=senior|family|audit: backend to UI only. Client messages are ignored. */
@Component
class EventsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventsWebSocketHandler.class);

    private final EventBus eventBus;

    EventsWebSocketHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String role = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("role");
        var parsed = ClientRole.parse(role);
        if (parsed.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("role must be senior, family or audit"));
            return;
        }
        eventBus.register(parsed.get(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Read-only channel.
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        log.debug("Transport error on /ws/events: {}", exception.getClass().getSimpleName());
        eventBus.unregister(session.getId());
        session.close(CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        eventBus.unregister(session.getId());
    }
}
