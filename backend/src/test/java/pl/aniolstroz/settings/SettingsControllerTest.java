package pl.aniolstroz.settings;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.risk.SensitivitySource;

/** BE-09: settings, consents (AUD-07) and sensitivity. Every call checked against contracts/openapi.yaml (TST-03). */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerTest {

    private static final String SPEC = "contract/openapi.yaml";
    private static final String FULL = """
            {"seniorConsent":true,"familyConsent":true,
             "contacts":[{"name":" Marek ","phone":"+48 601 234 567"}],
             "sensitivity":"sensitive","retentionDays":14,"seniorName":"Mama"}""";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SettingsService settings;
    @Autowired
    ConsentChecker consent;
    @Autowired
    SensitivitySource sensitivity;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    @AfterEach
    void defaults() {
        jdbc.sql("DELETE FROM settings").update();
    }

    private org.springframework.test.web.servlet.ResultActions save(String json) throws Exception {
        return mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void beforeTheSetupThereIsNoConsentAndSensitivityIsStandard() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.seniorConsent").value(false))
                .andExpect(jsonPath("$.familyConsent").value(false))
                .andExpect(jsonPath("$.sensitivity").value("standard"))
                .andExpect(jsonPath("$.retentionDays").value(30));
        assertThat(consent.hasConsent()).isFalse();
        assertThat(sensitivity.current()).isEqualTo(Sensitivity.STANDARD);
    }

    @Test
    void savedSettingsAreReturnedAndDriveConsentAndSensitivity() throws Exception {
        save(FULL).andExpect(status().isOk()).andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.contacts[0].name").value("Marek"))
                .andExpect(jsonPath("$.sensitivity").value("sensitive"));
        mockMvc.perform(get("/api/settings"))
                .andExpect(jsonPath("$.retentionDays").value(14))
                .andExpect(jsonPath("$.seniorName").value("Mama"));
        assertThat(consent.hasConsent()).isTrue();
        assertThat(sensitivity.current()).isEqualTo(Sensitivity.SENSITIVE);
    }

    @Test
    void bothConsentsAreNeeded() throws Exception {
        save(FULL.replace("\"familyConsent\":true", "\"familyConsent\":false")).andExpect(status().isOk());
        assertThat(consent.hasConsent()).isFalse();
    }

    @Test
    void invalidSettingsAreRefused() throws Exception {
        save(FULL.replace("14", "120")).andExpect(status().isBadRequest());
        save(FULL.replace("+48 601 234 567", "abc")).andExpect(status().isBadRequest());
        save(FULL.replace("\"sensitive\"", "\"paranoid\"")).andExpect(status().isBadRequest());
        save("{\"seniorConsent\":true}").andExpect(status().isBadRequest());
        assertThat(consent.hasConsent()).isFalse();
    }

    @Test
    void erasingAllDataKeepsTheSettings() throws Exception {
        save(FULL).andExpect(status().isOk());
        mockMvc.perform(delete("/api/data")).andExpect(status().isNoContent());
        assertThat(settings.current().seniorName()).isEqualTo("Mama");
    }

    @Test
    void resetGoesBackToTheDefaultsAndWithdrawsTheConsent() throws Exception {
        save(FULL).andExpect(status().isOk());
        assertThat(consent.hasConsent()).isTrue();

        mockMvc.perform(delete("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.seniorConsent").value(false))
                .andExpect(jsonPath("$.contacts.length()").value(0))
                .andExpect(jsonPath("$.seniorName").value(""));
        assertThat(consent.hasConsent()).isFalse();
        assertThat(sensitivity.current()).isEqualTo(Sensitivity.STANDARD);
    }
}
