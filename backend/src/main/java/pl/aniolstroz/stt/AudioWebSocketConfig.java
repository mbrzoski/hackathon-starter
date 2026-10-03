package pl.aniolstroz.stt;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pl.aniolstroz.config.AppProperties;

/** Registers /ws/audio (no STOMP, no SockJS, BE-03) and caps the message size in the WebSocket container (AUD-01). */
@Configuration(proxyBeanMethods = false)
class AudioWebSocketConfig implements WebSocketConfigurer {

    /** One frame is 3200 bytes; a generous cap keeps a misbehaving client from filling memory. */
    static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private final AudioWebSocketHandler handler;
    private final AppProperties properties;

    AudioWebSocketConfig(AudioWebSocketHandler handler, AppProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Same origins as /ws/events (API-04); empty means same origin only.
        registry.addHandler(handler, "/ws/audio")
                .setAllowedOrigins(properties.events().allowedOrigins().toArray(String[]::new));
    }

    /**
     * The limit lives in the embedded Tomcat (its WebSocket container reads these context parameters), so a message
     * over 64 KB is refused with close code 1009 before it reaches the handler. It applies to the whole container,
     * which is fine: /ws/events never receives more than a few bytes.
     */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> webSocketMessageLimit() {
        return factory -> factory.addContextCustomizers(context -> {
            context.addParameter("org.apache.tomcat.websocket.textBufferSize", String.valueOf(MAX_MESSAGE_BYTES));
            context.addParameter("org.apache.tomcat.websocket.binaryBufferSize", String.valueOf(MAX_MESSAGE_BYTES));
        });
    }
}
