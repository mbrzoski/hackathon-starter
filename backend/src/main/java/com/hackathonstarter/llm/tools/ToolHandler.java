package com.hackathonstarter.llm.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.hackathonstarter.llm.model.ToolDefinition;

/** Implement as a Spring bean ({@code @Component}) and it is automatically offered to the model. */
public interface ToolHandler {

    String name();

    String description();

    /** JSON Schema (type "object") describing the tool's input. */
    JsonNode inputSchema();

    /** Result is sent back to the model as text. Throw to report an error to the model. */
    String execute(JsonNode input) throws Exception;

    default ToolDefinition definition() {
        return new ToolDefinition(name(), description(), inputSchema());
    }
}
