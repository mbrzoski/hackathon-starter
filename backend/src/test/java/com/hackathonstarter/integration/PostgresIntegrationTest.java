package com.hackathonstarter.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathonstarter.llm.LlmCallLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full stack against a real PostgreSQL (Flyway included) with the mock LLM. Skipped automatically if Docker is missing. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired LlmCallLogRepository callLogs;

    @Test
    void healthReportsDatabaseUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.llm.provider").value("mock"));
    }

    @Test
    void chatWorksEndToEndAndIsAudited() throws Exception {
        long before = callLogs.count();

        mvc.perform(post("/api/llm/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("[mock] ping"));

        assertThat(callLogs.count()).isEqualTo(before + 1);
    }

    @Test
    void structuredOutputWorksEndToEndOffline() throws Exception {
        String body = """
                {"prompt":"x","schema":{"type":"object","required":["title"],
                 "properties":{"title":{"type":"string"},"score":{"type":"integer"}}}}""";

        mvc.perform(post("/api/llm/structured").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("mock"));
    }

    @Test
    void toolListContainsExampleTool() throws Exception {
        mvc.perform(get("/api/llm/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("get_current_time"));
    }
}
