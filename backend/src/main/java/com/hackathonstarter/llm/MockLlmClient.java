package com.hackathonstarter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmMessage;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.ToolDefinition;
import com.hackathonstarter.llm.model.Usage;
import java.util.List;
import java.util.UUID;

/** Offline, deterministic stand-in for Claude. Enable with {@code LLM_PROVIDER=mock}. */
public class MockLlmClient implements LlmClient {

    private final ObjectMapper mapper;
    private final String model;

    public MockLlmClient(ObjectMapper mapper, String model) {
        this.mapper = mapper;
        this.model = model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (request.forcedTool() != null) {
            ToolDefinition tool = request.tools().stream()
                    .filter(t -> t.name().equals(request.forcedTool()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Forced tool not provided: " + request.forcedTool()));
            JsonNode sample = sample(tool.inputSchema());
            return new LlmResponse(model,
                    List.of(new ContentBlock.ToolUse("mock_" + UUID.randomUUID(), tool.name(), sample)),
                    "tool_use", new Usage(1, 1));
        }
        String lastUserText = lastUserText(request.messages());
        return new LlmResponse(model,
                List.of(new ContentBlock.Text("[mock] " + lastUserText)),
                "end_turn", new Usage(lastUserText.length() / 4 + 1, lastUserText.length() / 4 + 3));
    }

    private static String lastUserText(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage m = messages.get(i);
            if (m.role() == LlmMessage.Role.USER) {
                for (ContentBlock b : m.content()) {
                    if (b instanceof ContentBlock.Text t) {
                        return t.text();
                    }
                }
            }
        }
        return "";
    }

    /** Builds a minimal value that satisfies the schema's declared types. */
    JsonNode sample(JsonNode schema) {
        String type = schema.path("type").asText("object");
        return switch (type) {
            case "string" -> mapper.getNodeFactory().textNode(firstEnum(schema, "mock"));
            case "integer", "number" -> mapper.getNodeFactory().numberNode(0);
            case "boolean" -> mapper.getNodeFactory().booleanNode(false);
            case "array" -> {
                ArrayNode arr = mapper.createArrayNode();
                if (schema.path("minItems").asInt(0) > 0 && schema.has("items")) {
                    arr.add(sample(schema.get("items")));
                }
                yield arr;
            }
            default -> {
                ObjectNode obj = mapper.createObjectNode();
                schema.path("properties").fields().forEachRemaining(e -> obj.set(e.getKey(), sample(e.getValue())));
                yield obj;
            }
        };
    }

    private static String firstEnum(JsonNode schema, String fallback) {
        JsonNode values = schema.path("enum");
        return values.isArray() && !values.isEmpty() ? values.get(0).asText() : fallback;
    }
}
