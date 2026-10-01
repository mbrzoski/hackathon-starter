package com.hackathonstarter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathonstarter.config.AppProperties;
import com.hackathonstarter.llm.LlmClient;
import com.hackathonstarter.llm.LlmException;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.Usage;
import com.hackathonstarter.llm.structured.StructuredOutputException;
import com.hackathonstarter.llm.structured.StructuredOutputService;
import com.hackathonstarter.llm.tools.ToolCallingService;
import com.hackathonstarter.llm.tools.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LlmController.class)
@EnableConfigurationProperties(AppProperties.class)
class LlmControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean LlmClient llm;
    @MockitoBean StructuredOutputService structured;
    @MockitoBean ToolCallingService toolCalling;
    @MockitoBean ToolRegistry registry;

    @Test
    void chatReturnsModelText() throws Exception {
        when(llm.complete(any())).thenReturn(
                new LlmResponse("m1", List.of(new ContentBlock.Text("pong")), "end_turn", new Usage(3, 4)));

        mvc.perform(post("/api/llm/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.text").value("pong"))
                .andExpect(jsonPath("$.usage.outputTokens").value(4));
    }

    @Test
    void blankMessageGivesUniform400() throws Exception {
        mvc.perform(post("/api/llm/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/llm/chat"))
                .andExpect(jsonPath("$.details[0]").value("message: must not be blank"));
    }

    @Test
    void malformedJsonGives400() throws Exception {
        mvc.perform(post("/api/llm/chat").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void llmFailureKeepsItsStatus() throws Exception {
        when(llm.complete(any())).thenThrow(new LlmException(HttpStatus.SERVICE_UNAVAILABLE, "no key"));

        mvc.perform(post("/api/llm/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("no key"));
    }

    @Test
    void structuredFailureIs422WithDetails() throws Exception {
        when(structured.generate(any(), any(), any()))
                .thenThrow(new StructuredOutputException("bad output", List.of("$.age: required")));

        mvc.perform(post("/api/llm/structured").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"p\",\"schema\":{\"type\":\"object\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0]").value("$.age: required"));
    }

    @Test
    void unexpectedErrorDoesNotLeakInternals() throws Exception {
        when(llm.complete(any())).thenThrow(new IllegalStateException("db password is hunter2"));

        mvc.perform(post("/api/llm/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }
}
