package pl.aniolstroz.events;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pl.aniolstroz.config.AppProperties;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class EventsWebSocketConfig implements WebSocketConfigurer {

    private final EventsWebSocketHandler handler;
    private final AppProperties properties;

    EventsWebSocketConfig(EventsWebSocketHandler handler, AppProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/events")
                .setAllowedOrigins(properties.events().allowedOrigins().toArray(String[]::new));
    }
}
