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

/** Every request and response is checked against contracts/openapi.yaml (TST-03). */
@SpringBootTest
@AutoConfigureMockMvc
class SeniorConfigControllerTest {

    private static final String SPEC = "contract/openapi.yaml";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SeniorConfigService config;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    @AfterEach
    void nothingSet() {
        jdbc.sql("DELETE FROM senior_config").update();
        jdbc.sql("DELETE FROM senior_keywords").update();
    }

    private org.springframework.test.web.servlet.ResultActions save(String phone) throws Exception {
        return save(phone, "[]");
    }

    private org.springframework.test.web.servlet.ResultActions save(String phone, String keywordsJson) throws Exception {
        return mockMvc.perform(put("/api/senior-config").contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyPhone\":\"" + phone + "\",\"keywords\":" + keywordsJson + "}"));
    }

    @Test
    void hasNoNumberUntilTheFamilySetsOne() throws Exception {
        mockMvc.perform(get("/api/senior-config"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.familyPhone").value(""));
    }

    @Test
    void putStoresTheNumberAndGetReturnsIt() throws Exception {
        save("+48 602 000 222").andExpect(status().isOk()).andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.familyPhone").value("+48 602 000 222"));
        assertThat(config.current().familyPhone()).isEqualTo("+48 602 000 222");
        mockMvc.perform(get("/api/senior-config")).andExpect(jsonPath("$.familyPhone").value("+48 602 000 222"));
    }

    @Test
    void anEmptyNumberClearsIt() throws Exception {
        save("602000222").andExpect(status().isOk());
        save("").andExpect(status().isOk()).andExpect(jsonPath("$.familyPhone").value(""));
        assertThat(config.current().familyPhone()).isEmpty();
    }

    @Test
    void keywordsAreStoredInOrderCleanedAndWithoutDuplicates() throws Exception {
        save("", "[\"akt własności\", \"  Dowód   osobisty \", \"AKT WŁASNOŚCI\", \"x\", \"  \"]")
                .andExpect(status().isBadRequest()); // "x" is too short for the contract
        save("", "[\"akt własności\", \"  Dowód   osobisty \", \"AKT WŁASNOŚCI\"]")
                .andExpect(status().isOk()).andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.keywords[0]").value("akt własności"))
                .andExpect(jsonPath("$.keywords[1]").value("Dowód osobisty"));
        mockMvc.perform(get("/api/senior-config")).andExpect(jsonPath("$.keywords[1]").value("Dowód osobisty"));
        assertThat(config.keywords()).containsExactly("akt własności", "Dowód osobisty");
    }

    @Test
    void tooManyKeywordsAreRefused() throws Exception {
        String many = java.util.stream.IntStream.range(0, 31).mapToObj(i -> "\"słowo" + i + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        save("", many).andExpect(status().isBadRequest());
    }

    @Test
    void aMalformedNumberIsRefused() throws Exception {
        save("nie numer").andExpect(status().isBadRequest());
        save("123").andExpect(status().isBadRequest());
        assertThat(config.current().familyPhone()).isEmpty();
    }

    @Test
    void erasingAllDataLeavesTheSetting() throws Exception {
        save("602000222").andExpect(status().isOk());
        mockMvc.perform(delete("/api/data")).andExpect(status().isNoContent());
        assertThat(config.current().familyPhone()).isEqualTo("602000222");
    }
}
