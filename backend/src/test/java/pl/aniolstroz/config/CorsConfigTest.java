package pl.aniolstroz.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.events.allowed-origins=https://demo.example")
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void allowsThePublicOrigin() throws Exception {
        mockMvc.perform(options("/api/status")
                        .header("Origin", "https://demo.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://demo.example"));
    }

    @Test
    void rejectsAnyOtherOrigin() throws Exception {
        mockMvc.perform(options("/api/status")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    /** The family panel erases one call or resets the settings with DELETE (a 403 here broke it in the dev setup). */
    @Test
    void allowsDeleteFromThePublicOrigin() throws Exception {
        mockMvc.perform(options("/api/calls/call-1")
                        .header("Origin", "https://demo.example")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://demo.example"));
    }
}
