package pl.aniolstroz.alerts;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.CallState;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.AlertDecisionEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/** Every request and response is checked against contracts/openapi.yaml (TST-03). */
@SpringBootTest
@AutoConfigureMockMvc
class AlertsControllerTest {

    private static final String SPEC = "contract/openapi.yaml";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CallService calls;
    @Autowired
    AppProperties properties;
    @MockitoSpyBean
    EventBus eventBus;

    @AfterEach
    void endCall() {
        calls.end();
    }

    private CallState say(CallState call, String text) {
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, text, true, SpeakerLabel.B, null));
        return call;
    }

    /** Starts a SCRIPTED call that raises a MEDIUM alert and returns the alert id. */
    private String raiseAlert(CallState call) {
        say(call, "Mówi policja. Nikomu nie mów.");
        return call.alerts().get(0).alertId();
    }

    private ResultActions decide(String alertId, String body) throws Exception {
        return mockMvc.perform(post("/api/alerts/" + alertId + "/decision")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void decisionIsStoredAndReturnedWithAlertIdAndTimeFromTheServer() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));

        decide(alertId, "{\"actor\":\"senior\",\"decision\":\"hung_up\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertId").value(alertId))
                .andExpect(jsonPath("$.actor").value("senior"))
                .andExpect(jsonPath("$.decision").value("hung_up"))
                .andExpect(jsonPath("$.at").isNotEmpty())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void clientCannotSupplyAlertIdOrTime() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));

        decide(alertId, "{\"actor\":\"senior\",\"decision\":\"hung_up\",\"at\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void decisionPublishesAlertDecisionEvent() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));

        decide(alertId, "{\"actor\":\"family\",\"decision\":\"confirmed_scam\"}").andExpect(status().isCreated());

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        assertThat(captor.getAllValues()).filteredOn(AlertDecisionEvent.class::isInstance)
                .map(e -> ((AlertDecisionEvent) e).payload())
                .anySatisfy(d -> assertThat(d.alertId()).isEqualTo(alertId));
    }

    @Test
    void falseAlarmAndConfirmedScamGoToTheLabelsFile() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));

        decide(alertId, "{\"actor\":\"senior\",\"decision\":\"false_alarm\"}").andExpect(status().isCreated());

        assertThat(labelsContent()).contains(alertId).contains("false_alarm");
    }

    @Test
    void unknownAlertIsProblemDetail404() throws Exception {
        decide("no-such-alert", "{\"actor\":\"senior\",\"decision\":\"false_alarm\"}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void invalidBodiesAreProblemDetail400() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));

        for (String body : List.of(
                "{\"actor\":\"senior\"}",
                "{\"decision\":\"hung_up\"}",
                "{\"actor\":\"neighbour\",\"decision\":\"hung_up\"}",
                "{\"actor\":\"senior\",\"decision\":\"shrug\"}",
                "{\"actor\":\"family\",\"decision\":\"confirmed_scam\",\"ignoredStages\":[\"NOT_A_STAGE\"]}",
                "not json")) {
            decide(alertId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void decisionsTheActorCannotMakeAreProblemDetail400() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));

        decide(alertId, "{\"actor\":\"family\",\"decision\":\"hung_up\"}")
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SPEC));
        decide(alertId, "{\"actor\":\"senior\",\"decision\":\"confirmed_scam\"}").andExpect(status().isBadRequest());
        decide(alertId, "{\"actor\":\"senior\",\"decision\":\"false_alarm\",\"ignoredStages\":[\"AUTHORITY_CLAIM\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void familyIgnoredStagesLowerTheRiskOfTheActiveCall() throws Exception {
        CallState call = calls.start(Mode.SCRIPTED);
        String alertId = raiseAlert(call);
        say(call, "Proszę wypłacić gotówkę.");
        assertThat(call.level()).isEqualTo(RiskLevel.HIGH);

        decide(alertId, "{\"actor\":\"family\",\"decision\":\"false_alarm\",\"ignoredStages\":[\"MONEY_REQUEST\"]}")
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(SPEC));

        assertThat(call.level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void severalDecisionsAppearWithTheAlertInTheListNewestAlertFirst() throws Exception {
        String alertId = raiseAlert(calls.start(Mode.SCRIPTED));
        decide(alertId, "{\"actor\":\"senior\",\"decision\":\"called_trusted\"}").andExpect(status().isCreated());
        decide(alertId, "{\"actor\":\"family\",\"decision\":\"confirmed_scam\"}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$[0].alert.alertId").value(alertId))
                .andExpect(jsonPath("$[0].alert.mode").value("SCRIPTED"))
                .andExpect(jsonPath("$[0].decisions.length()").value(2))
                .andExpect(jsonPath("$[0].decisions[0].decision").value("called_trusted"))
                .andExpect(jsonPath("$[0].decisions[1].decision").value("confirmed_scam"));
    }

    @Test
    void listIncludesAlertsOfFinishedCallsAndHonoursTheLimit() throws Exception {
        String first = raiseAlert(calls.start(Mode.SCRIPTED));
        calls.end();
        String second = raiseAlert(calls.start(Mode.SCRIPTED));
        calls.end();

        mockMvc.perform(get("/api/alerts").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].alert.alertId").value(second))
                .andExpect(jsonPath("$[1].alert.alertId").value(first));
    }

    @Test
    void limitOutsideTheContractRangeIsProblemDetail400() throws Exception {
        for (String limit : List.of("0", "101", "abc")) {
            mockMvc.perform(get("/api/alerts").param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    private String labelsContent() throws IOException {
        Path file = Path.of(properties.labels().file());
        return Files.exists(file) ? Files.readString(file) : "";
    }
}
