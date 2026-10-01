package com.hackathonstarter.llm.model;

import com.fasterxml.jackson.databind.JsonNode;

/** A function the model may call. {@code inputSchema} is a JSON Schema of type "object". */
public record ToolDefinition(String name, String description, JsonNode inputSchema) {}
