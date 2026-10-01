package com.hackathonstarter.llm.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.hackathonstarter.llm.LlmClient;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmMessage;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.ToolDefinition;
import com.hackathonstarter.llm.model.Usage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs the classic loop: model asks for tools, we execute them, feed results back, until it answers in text. */
@Service
public class ToolCallingService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingService.class);
    public static final int DEFAULT_MAX_ITERATIONS = 5;

    public record ToolStep(String tool, JsonNode input, String output, boolean error) {}

    public record Result(String text, List<ToolStep> steps, Usage usage, boolean truncated) {}

    private final LlmClient llm;
    private final ToolRegistry registry;

    public ToolCallingService(LlmClient llm, ToolRegistry registry) {
        this.llm = llm;
        this.registry = registry;
    }

    public Result run(String system, String prompt, Collection<String> toolNames, Integer maxIterations) {
        List<ToolDefinition> tools = registry.definitions(toolNames);
        int limit = maxIterations == null ? DEFAULT_MAX_ITERATIONS : Math.max(1, Math.min(maxIterations, 10));

        List<LlmMessage> messages = new ArrayList<>(List.of(LlmMessage.user(prompt)));
        List<ToolStep> steps = new ArrayList<>();
        Usage total = Usage.ZERO;

        for (int i = 0; i < limit; i++) {
            LlmResponse response = llm.complete(LlmRequest.of(system, messages).withTools(tools, null));
            total = total.plus(response.usage());
            List<ContentBlock.ToolUse> uses = response.toolUses();
            if (uses.isEmpty()) {
                return new Result(response.text(), steps, total, false);
            }
            messages.add(LlmMessage.assistant(response.content()));
            List<ContentBlock> results = new ArrayList<>();
            for (ContentBlock.ToolUse use : uses) {
                results.add(execute(use, steps));
            }
            messages.add(LlmMessage.user(results));
        }
        return new Result("", steps, total, true);
    }

    private ContentBlock.ToolResult execute(ContentBlock.ToolUse use, List<ToolStep> steps) {
        ToolHandler handler = registry.find(use.name()).orElse(null);
        if (handler == null) {
            steps.add(new ToolStep(use.name(), use.input(), "Unknown tool", true));
            return new ContentBlock.ToolResult(use.id(), "Unknown tool: " + use.name(), true);
        }
        try {
            String output = handler.execute(use.input());
            steps.add(new ToolStep(use.name(), use.input(), output, false));
            return new ContentBlock.ToolResult(use.id(), output, false);
        } catch (Exception e) {
            log.warn("Tool {} failed: {}", use.name(), e.getMessage());
            steps.add(new ToolStep(use.name(), use.input(), e.getMessage(), true));
            return new ContentBlock.ToolResult(use.id(), "Tool error: " + e.getMessage(), true);
        }
    }
}
