package pl.aniolstroz.demo;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Every call is checked against contracts/openapi.yaml (request and response), as TST-03 asks. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    private static final String SPEC = "contract/openapi.yaml";

    /** Playback stays parked on its first pause until stop(), so a started replay stays "running". */
    @TestConfiguration
    static class ParkedSleeper {
        @Bean
        @Primary
        Sleeper parkedSleeper() {
            return duration -> new CountDownLatch(1).await();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @AfterEach
    void stopReplay() throws Exception {
        mockMvc.perform(post("/api/demo/stop"));
    }

    private org.springframework.test.web.servlet.ResultActions replay(String body) throws Exception {
        return mockMvc.perform(post("/api/demo/replay").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void listsScenariosWithIdTitleAndDescription() throws Exception {
        mockMvc.perform(get("/api/demo/scenarios"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].scenarioId").value("01-fake-police-classic"))
                .andExpect(jsonPath("$[0].title").value("Klasyczny fałszywy policjant"))
                .andExpect(jsonPath("$[0].description").isNotEmpty());
    }

    @Test
    void replayOfKnownScenarioIsAccepted() throws Exception {
        replay("{\"scenarioId\":\"01-fake-police-classic\",\"mode\":\"SCRIPTED\",\"speed\":4}")
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void speedIsOptional() throws Exception {
        replay("{\"scenarioId\":\"01-fake-police-classic\",\"mode\":\"SCRIPTED\"}")
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void unknownScenarioIsProblemDetail404() throws Exception {
        replay("{\"scenarioId\":\"no-such-scenario\",\"mode\":\"SCRIPTED\"}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void modesOtherThanScriptedAreRejectedForNow() throws Exception {
        replay("{\"scenarioId\":\"01-fake-police-classic\",\"mode\":\"LIVE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void speedOutsideTheContractRangeIsRejected() throws Exception {
        replay("{\"scenarioId\":\"01-fake-police-classic\",\"mode\":\"SCRIPTED\",\"speed\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void secondReplayWhileOneRunsIsProblemDetail409() throws Exception {
        String body = "{\"scenarioId\":\"01-fake-police-classic\",\"mode\":\"SCRIPTED\"}";
        replay(body).andExpect(status().isAccepted());

        replay(body)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void stopEndsTheReplayAndAllowsANewOne() throws Exception {
        String body = "{\"scenarioId\":\"01-fake-police-classic\",\"mode\":\"SCRIPTED\"}";
        replay(body).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/demo/stop"))
                .andExpect(status().isNoContent())
                .andExpect(openApi().isValid(SPEC));

        replay(body).andExpect(status().isAccepted());
    }

    @Test
    void stopWithoutReplayIsStillNoContent() throws Exception {
        mockMvc.perform(post("/api/demo/stop"))
                .andExpect(status().isNoContent())
                .andExpect(openApi().isValid(SPEC));
    }
}
