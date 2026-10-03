package pl.aniolstroz.settings;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Every request and response is checked against contracts/openapi.yaml (TST-03). */
@SpringBootTest
@AutoConfigureMockMvc
class ProtectionControllerTest {

    private static final String SPEC = "contract/openapi.yaml";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ProtectionService protection;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    @AfterEach
    void switchedOn() {
        jdbc.sql("DELETE FROM protection").update();
    }

    @Test
    void isOnWhenNobodyChangedIt() throws Exception {
        mockMvc.perform(get("/api/protection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void putSwitchesItOffAndOnAndTheChoiceIsStored() throws Exception {
        mockMvc.perform(put("/api/protection").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(openApi().isValid(SPEC));
        assertThat(protection.enabled()).isFalse();
        mockMvc.perform(get("/api/protection")).andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(put("/api/protection").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC));
        assertThat(protection.enabled()).isTrue();
    }

    @Test
    void anInvalidBodyIsRefused() throws Exception {
        mockMvc.perform(put("/api/protection").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":\"maybe\"}"))
                .andExpect(status().isBadRequest());
    }
}
