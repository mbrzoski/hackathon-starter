package pl.aniolstroz.demo;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class DevEmitControllerTest {

    private static final String SPEC = "contract/openapi.yaml";

    private static final String VALID_EVENT = """
            {"type":"system.status","mode":"MOCK","at":"2026-10-03T21:00:00Z",
             "payload":{"component":"ai","state":"degraded","message":"test","at":"2026-10-03T21:00:00Z"}}""";

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    class Dev {

        @Autowired
        MockMvc mockMvc;

        @Test
        void validEventIsAccepted() throws Exception {
            mockMvc.perform(post("/api/dev/emit").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                    .andExpect(status().isAccepted())
                    .andExpect(openApi().isValid(SPEC));
        }

        @Test
        void eventWithoutPayloadIsRejected() throws Exception {
            String invalid = "{\"type\":\"system.status\",\"mode\":\"MOCK\",\"at\":\"2026-10-03T21:00:00Z\"}";

            mockMvc.perform(post("/api/dev/emit").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    // The prod profile refuses to start without ANTHROPIC_API_KEY; this test is about /api/dev, not the AI.
    @SpringBootTest(properties = "app.mode=MOCK")
    @AutoConfigureMockMvc
    @ActiveProfiles("prod")
    class Prod {

        @Autowired
        MockMvc mockMvc;

        @Test
        void emitEndpointDoesNotExist() throws Exception {
            mockMvc.perform(post("/api/dev/emit").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                    .andExpect(status().isNotFound());
        }
    }
}
