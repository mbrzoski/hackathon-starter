package com.hackathonstarter.llm.model;

import java.util.List;

/**
 * Provider-neutral request. {@code model}, {@code maxTokens} and {@code temperature} are optional overrides;
 * when null the configured defaults apply. {@code forcedTool} makes the model answer by calling that tool.
 */
public record LlmRequest(
        String system,
        List<LlmMessage> messages,
        List<ToolDefinition> tools,
        String forcedTool,
        String model,
        Integer maxTokens,
        Double temperature) {

    public static LlmRequest of(String system, String userMessage) {
        return new LlmRequest(system, List.of(LlmMessage.user(userMessage)), List.of(), null, null, null, null);
    }

    public static LlmRequest of(String system, List<LlmMessage> messages) {
        return new LlmRequest(system, messages, List.of(), null, null, null, null);
    }

    public LlmRequest withTools(List<ToolDefinition> newTools, String newForcedTool) {
        return new LlmRequest(system, messages, newTools, newForcedTool, model, maxTokens, temperature);
    }

    public LlmRequest withMessages(List<LlmMessage> newMessages) {
        return new LlmRequest(system, newMessages, tools, forcedTool, model, maxTokens, temperature);
    }

    public LlmRequest withModel(String newModel) {
        return new LlmRequest(system, messages, tools, forcedTool, newModel, maxTokens, temperature);
    }
}
