package com.hackathonstarter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathonstarter.config.AppProperties;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.ToolDefinition;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class AnthropicClaudeClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AnthropicClaudeClient clientAnswering(int status, String responseJson, String apiKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var cfg = new AppProperties.Llm("anthropic", apiKey, "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-model", 256, Duration.ofSeconds(5), "2023-06-01");
        return new AnthropicClaudeClient(cfg, RestClient.builder(), mapper);
    }

    @Test
    void sendsExpectedRequestAndParsesTextResponse() throws Exception {
        var client = clientAnswering(200, """
                {"model":"test-model","stop_reason":"end_turn",
                 "content":[{"type":"text","text":"Hello"}],
                 "usage":{"input_tokens":11,"output_tokens":7}}""", "secret");

        LlmResponse response = client.complete(LlmRequest.of("be brief", "Hi"));

        assertThat(response.text()).isEqualTo("Hello");
        assertThat(response.usage().inputTokens()).isEqualTo(11);
        assertThat(response.usage().outputTokens()).isEqualTo(7);
        assertThat(lastApiKey.get()).isEqualTo("secret");
        JsonNode sent = mapper.readTree(lastBody.get());
        assertThat(sent.path("model").asText()).isEqualTo("test-model");
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(256);
        assertThat(sent.path("system").asText()).isEqualTo("be brief");
        assertThat(sent.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(sent.at("/messages/0/content/0/text").asText()).isEqualTo("Hi");
    }

    @Test
    void parsesToolUseAndSendsToolDefinitionWithForcedChoice() throws Exception {
        var client = clientAnswering(200, """
                {"model":"test-model","stop_reason":"tool_use",
                 "content":[{"type":"tool_use","id":"tu_1","name":"lookup","input":{"q":"x"}}],
                 "usage":{"input_tokens":1,"output_tokens":1}}""", "secret");
        JsonNode schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}");

        LlmResponse response = client.complete(
                LlmRequest.of(null, "go").withTools(List.of(new ToolDefinition("lookup", "d", schema)), "lookup"));

        ContentBlock.ToolUse use = response.toolUses().get(0);
        assertThat(use.id()).isEqualTo("tu_1");
        assertThat(use.input().path("q").asText()).isEqualTo("x");
        JsonNode sent = mapper.readTree(lastBody.get());
        assertThat(sent.at("/tools/0/name").asText()).isEqualTo("lookup");
        assertThat(sent.at("/tools/0/input_schema/type").asText()).isEqualTo("object");
        assertThat(sent.at("/tool_choice/type").asText()).isEqualTo("tool");
        assertThat(sent.at("/tool_choice/name").asText()).isEqualTo("lookup");
    }

    @Test
    void failsFastWithoutApiKey() throws Exception {
        var client = clientAnswering(200, "{}", "");

        assertThatThrownBy(() -> client.complete(LlmRequest.of(null, "Hi")))
                .isInstanceOfSatisfying(LlmException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(lastBody.get()).isNull();
    }

    @Test
    void mapsUpstreamErrorToBadGateway() throws Exception {
        var client = clientAnswering(500, "{\"error\":\"boom\"}", "secret");

        assertThatThrownBy(() -> client.complete(LlmRequest.of(null, "Hi")))
                .isInstanceOfSatisfying(LlmException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void mapsRateLimitTo429() throws Exception {
        var client = clientAnswering(429, "{\"error\":\"slow down\"}", "secret");

        assertThatThrownBy(() -> client.complete(LlmRequest.of(null, "Hi")))
                .isInstanceOfSatisfying(LlmException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
