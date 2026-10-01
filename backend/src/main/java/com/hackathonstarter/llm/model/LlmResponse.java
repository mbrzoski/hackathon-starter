package com.hackathonstarter.llm.model;

import java.util.List;

public record LlmResponse(String model, List<ContentBlock> content, String stopReason, Usage usage) {

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof ContentBlock.Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    public List<ContentBlock.ToolUse> toolUses() {
        return content.stream()
                .filter(ContentBlock.ToolUse.class::isInstance)
                .map(ContentBlock.ToolUse.class::cast)
                .toList();
    }
}
