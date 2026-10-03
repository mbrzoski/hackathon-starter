package pl.aniolstroz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traces every request that reaches the backend: method, path, status, time, who asked. The point is to see whether
 * the frontend talks to the backend at all, and which device it is (the user agent tells a phone from a laptop).
 * Bodies, cookies and authorisation headers are never read or logged (rule 8, OBS-05). The WebSocket handshakes of
 * {@code /ws/events} and {@code /ws/audio} come through here too (status 101).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestTraceFilter extends OncePerRequestFilter {

    private static final int USER_AGENT_LENGTH = 90;

    private final Clock clock;

    RequestTraceFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = clock.millis();
        try {
            chain.doFilter(request, response);
        } finally {
            String query = request.getQueryString();
            Trace.flow("http | {} {}{} -> {} in {} ms | client={} origin={} ua={}",
                    request.getMethod(), request.getRequestURI(), query == null ? "" : "?" + Trace.oneLine(query, 200),
                    response.getStatus(), clock.millis() - start, request.getRemoteAddr(),
                    Trace.oneLine(request.getHeader("Origin"), 100),
                    Trace.oneLine(request.getHeader("User-Agent"), USER_AGENT_LENGTH));
        }
    }
}
