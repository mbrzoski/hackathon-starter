package com.hackathonstarter.llm.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Provider-neutral message content: plain text, a tool request from the model, or a tool result from us. */
public sealed interface ContentBlock {

    record Text(String text) implements ContentBlock {}

    record ToolUse(String id, String name, JsonNode input) implements ContentBlock {}

    record ToolResult(String toolUseId, String content, boolean isError) implements ContentBlock {}
}
