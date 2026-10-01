package com.hackathonstarter.llm.structured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathonstarter.llm.LlmClient;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmMessage;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.ToolDefinition;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Gets JSON that is guaranteed to match a caller-supplied JSON Schema. The model is forced to answer through a
 * tool whose input schema is the target schema; the result is validated locally and re-requested with the
 * validation errors if it does not conform.
 */
@Service
public class StructuredOutputService {

    static final String TOOL_NAME = "structured_output";
    static final int MAX_ATTEMPTS = 2;

    private final LlmClient llm;
    private final ObjectMapper mapper;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public StructuredOutputService(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public <T> T generate(String system, String prompt, JsonNode schema, Class<T> type) {
        JsonNode data = generate(system, prompt, schema);
        try {
            return mapper.treeToValue(data, type);
        } catch (JsonProcessingException e) {
            throw new StructuredOutputException("Validated JSON could not be mapped to " + type.getSimpleName(),
                    List.of(e.getOriginalMessage()));
        }
    }

    public JsonNode generate(String system, String prompt, JsonNode schema) {
        if (!"object".equals(schema.path("type").asText())) {
            throw new IllegalArgumentException("Schema must be a JSON Schema with \"type\": \"object\" at the root");
        }
        JsonSchema compiled = schemaFactory.getSchema(schema);
        ToolDefinition tool = new ToolDefinition(TOOL_NAME, "Return the final answer in exactly this structure.", schema);

        List<LlmMessage> messages = new ArrayList<>(List.of(LlmMessage.user(prompt)));
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            LlmResponse response = llm.complete(LlmRequest.of(system, messages).withTools(List.of(tool), TOOL_NAME));
            List<ContentBlock.ToolUse> uses = response.toolUses();
            if (uses.isEmpty()) {
                throw new StructuredOutputException("Model did not return structured output", List.of("No tool call in response"));
            }
            ContentBlock.ToolUse use = uses.get(0);
            Set<ValidationMessage> problems = compiled.validate(use.input());
            if (problems.isEmpty()) {
                return use.input();
            }
            lastErrors = problems.stream().map(ValidationMessage::getMessage).sorted().toList();
            messages.add(LlmMessage.assistant(response.content()));
            messages.add(LlmMessage.user(List.of(new ContentBlock.ToolResult(
                    use.id(), "Invalid output, fix and call the tool again: " + String.join("; ", lastErrors), true))));
        }
        throw new StructuredOutputException("Model output did not match the schema after " + MAX_ATTEMPTS + " attempts", lastErrors);
    }
}
