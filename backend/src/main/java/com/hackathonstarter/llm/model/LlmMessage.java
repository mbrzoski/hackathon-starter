package com.hackathonstarter.llm.model;

import java.util.List;

public record LlmMessage(Role role, List<ContentBlock> content) {

    public enum Role { USER, ASSISTANT }

    public static LlmMessage user(String text) {
        return new LlmMessage(Role.USER, List.of(new ContentBlock.Text(text)));
    }

    public static LlmMessage user(List<ContentBlock> blocks) {
        return new LlmMessage(Role.USER, blocks);
    }

    public static LlmMessage assistant(List<ContentBlock> blocks) {
        return new LlmMessage(Role.ASSISTANT, blocks);
    }
}
