package pl.aniolstroz.demo;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** The simulated incoming phone call: REST checked against the contract, and what a screen that connects later sees. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.events.heartbeat-interval-ms=3600000")
@AutoConfigureMockMvc
class PhoneCallSimulationTest {

    private static final String SPEC = "contract/openapi.yaml";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PhoneCallSimulation simulation;

    @Autowired
    ObjectMapper mapper;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @AfterEach
    void off() {
        simulation.set(false);
    }

    @Test
    void startsOffWithAMadeUpNumber() throws Exception {
        mockMvc.perform(get("/api/demo/phone-call"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.number").value("+48 600 100 200"));
    }

    @Test
    void putSwitchesItOnAndOff() throws Exception {
        mockMvc.perform(put("/api/demo/phone-call").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.active").value(true));
        assertThat(simulation.current().active()).isTrue();

        mockMvc.perform(put("/api/demo/phone-call").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(simulation.current().active()).isFalse();
    }

    @Test
    void aBodyOutsideTheContractIsRefused() throws Exception {
        mockMvc.perform(put("/api/demo/phone-call").contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(status().isBadRequest());
    }

    private final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();

    private WebSocketSession connect() throws Exception {
        return new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
                events.add(mapper.readTree(m.getPayload()));
            }
        }, "ws://localhost:" + port + "/ws/events?role=senior").get(5, TimeUnit.SECONDS);
    }

    private JsonNode awaitPhoneCall() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode e = events.poll(100, TimeUnit.MILLISECONDS);
            if (e != null && "phone.call".equals(e.get("type").asText())) {
                return e;
            }
        }
        throw new AssertionError("phone.call did not arrive within 5 s");
    }

    @Test
    void everyScreenHearsAboutItAndALateScreenGetsItInTheSnapshot() throws Exception {
        WebSocketSession early = connect();
        Thread.sleep(300);

        simulation.set(true);
        JsonNode on = awaitPhoneCall();
        assertThat(on.get("mode").asText()).isEqualTo("SCRIPTED");
        assertThat(on.at("/payload/active").asBoolean()).isTrue();
        assertThat(on.at("/payload/number").asText()).isEqualTo("+48 600 100 200");

        events.clear();
        WebSocketSession late = connect();
        assertThat(awaitPhoneCall().at("/payload/active").asBoolean()).isTrue();

        simulation.set(false);
        assertThat(awaitPhoneCall().at("/payload/active").asBoolean()).isFalse();
        early.close();
        late.close();

        events.clear();
        WebSocketSession afterwards = connect();
        Thread.sleep(500);
        assertThat(events).noneMatch(e -> "phone.call".equals(e.get("type").asText()));
        afterwards.close();
    }
}
