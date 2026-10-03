package pl.aniolstroz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

/** The trace of the process: the words stay out of the log unless asked for, and nothing can forge a log line. */
class TraceTest {

    @Test
    void theContentTraceIsOffUnlessItsLoggerIsSwitchedOn() {
        try (LogCapture off = LogCapture.start(false)) {
            assertThat(Trace.content()).isFalse();
            Trace.flow("flow line {}", 1);
            Trace.content("secret words {}", "mówi policja");

            assertThat(off.all()).contains("flow line 1").doesNotContain("mówi policja");
        }
        try (LogCapture on = LogCapture.start(true)) {
            assertThat(Trace.content()).isTrue();
            Trace.content("secret words {}", "mówi policja");

            assertThat(on.all()).contains("secret words mówi policja");
        }
    }

    @Test
    void textOnOneLineHasNoLineBreaksAndIsCutAtTheLimit() {
        assertThat(Trace.oneLine("a\nb\r\nc\td")).isEqualTo("a b c d");
        assertThat(Trace.oneLine("x".repeat(30), 10)).isEqualTo("xxxxxxxxxx...(30 chars)");
        assertThat(Trace.oneLine("short", 10)).isEqualTo("short");
        assertThat(Trace.oneLine(null)).isEqualTo("-");
    }

    @Test
    void idsAreShortenedForTheLog() {
        assertThat(Trace.id("bf5ff245-f413-41c6-8456-e65d49ac1718")).isEqualTo("bf5ff245");
        assertThat(Trace.id("s1")).isEqualTo("s1");
        assertThat(Trace.id(null)).isEqualTo("-");
    }

    @Test
    void aRequestIsTracedWithItsOutcomeAndWhoAskedButNeverItsBodyOrSecrets() throws Exception {
        var filter = new RequestTraceFilter(Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));
        var request = new MockHttpServletRequest("PUT", "/api/protection");
        request.setQueryString("a=1");
        request.setRemoteAddr("172.19.0.1");
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)");
        request.addHeader(HttpHeaders.ORIGIN, "https://172.20.10.4");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer super-secret-token");
        request.addHeader(HttpHeaders.COOKIE, "session=super-secret-cookie");
        request.setContent("{\"enabled\":false,\"note\":\"mówi policja\"}".getBytes());
        var response = new MockHttpServletResponse();
        response.setStatus(200);

        try (LogCapture log = LogCapture.start(true)) {
            filter.doFilter(request, response, new MockFilterChain());

            assertThat(log.all())
                    .contains("http | PUT /api/protection?a=1 -> 200")
                    .contains("client=172.19.0.1")
                    .contains("origin=https://172.20.10.4")
                    .contains("iPhone")
                    .doesNotContain("super-secret").doesNotContain("mówi policja");
        }
    }

    @Test
    void aFailingRequestIsStillTraced() {
        var filter = new RequestTraceFilter(Clock.systemUTC());
        var request = new MockHttpServletRequest("GET", "/api/boom");
        var response = new MockHttpServletResponse();

        try (LogCapture log = LogCapture.start(false)) {
            assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                throw new java.io.IOException("boom");
            })).isInstanceOf(java.io.IOException.class);

            assertThat(log.all()).contains("http | GET /api/boom ->");
        }
    }
}
