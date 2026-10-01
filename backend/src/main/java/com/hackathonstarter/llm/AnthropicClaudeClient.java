package com.hackathonstarter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hackathonstarter.config.AppProperties;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmMessage;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.ToolDefinition;
import com.hackathonstarter.llm.model.Usage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Adapter for the Anthropic Messages API (POST /v1/messages). The API key comes from configuration only. */
public class AnthropicClaudeClient implements LlmClient {

    private final AppProperties.Llm config;
    private final ObjectMapper mapper;
    private final RestClient http;

    public AnthropicClaudeClient(AppProperties.Llm config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.timeout());
        factory.setReadTimeout(config.timeout());
        this.http = builder
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .defaultHeader("anthropic-version", config.anthropicVersion())
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!config.apiKeyConfigured()) {
            throw new LlmException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ANTHROPIC_API_KEY is not configured (or run with LLM_PROVIDER=mock)");
        }
        try {
            JsonNode body = http.post()
                    .uri("/v1/messages")
                    .header("x-api-key", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toBody(request))
                    .retrieve()
                    .body(JsonNode.class);
            return parse(body);
        } catch (RestClientResponseException e) {
            HttpStatus status = e.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
            throw new LlmException(status, "Claude API returned " + e.getStatusCode().value() + ": " + abbreviate(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new LlmException(HttpStatus.BAD_GATEWAY, "Claude API unreachable: " + e.getMessage(), e);
        }
    }

    ObjectNode toBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model() != null ? request.model() : config.model());
        body.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : config.maxTokens());
        if (request.system() != null && !request.system().isBlank()) {
            body.put("system", request.system());
        }
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode m = messages.addObject();
            m.put("role", message.role().name().toLowerCase());
            ArrayNode content = m.putArray("content");
            message.content().forEach(block -> content.add(blockToJson(block)));
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode t = tools.addObject();
                t.put("name", tool.name());
                t.put("description", tool.description());
                t.set("input_schema", tool.inputSchema());
            }
            if (request.forcedTool() != null) {
                body.putObject("tool_choice").put("type", "tool").put("name", request.forcedTool());
            }
        }
        return body;
    }

    private ObjectNode blockToJson(ContentBlock block) {
        ObjectNode node = mapper.createObjectNode();
        switch (block) {
            case ContentBlock.Text t -> node.put("type", "text").put("text", t.text());
            case ContentBlock.ToolUse u -> {
                node.put("type", "tool_use").put("id", u.id()).put("name", u.name());
                node.set("input", u.input());
            }
            case ContentBlock.ToolResult r -> {
                node.put("type", "tool_result").put("tool_use_id", r.toolUseId()).put("content", r.content());
                if (r.isError()) {
                    node.put("is_error", true);
                }
            }
        }
        return node;
    }

    LlmResponse parse(JsonNode body) {
        if (body == null) {
            throw new LlmException(HttpStatus.BAD_GATEWAY, "Claude API returned an empty body");
        }
        List<ContentBlock> content = new ArrayList<>();
        for (JsonNode block : body.path("content")) {
            switch (block.path("type").asText()) {
                case "text" -> content.add(new ContentBlock.Text(block.path("text").asText()));
                case "tool_use" -> content.add(new ContentBlock.ToolUse(
                        block.path("id").asText(), block.path("name").asText(), block.path("input")));
                default -> { /* thinking and other block types are ignored by this generic adapter */ }
            }
        }
        JsonNode usage = body.path("usage");
        return new LlmResponse(
                body.path("model").asText(config.model()),
                content,
                body.path("stop_reason").asText(null),
                new Usage(usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt()));
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
