package com.hackathonstarter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hackathonstarter.llm.LlmClient;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.ToolDefinition;
import com.hackathonstarter.llm.model.Usage;
import com.hackathonstarter.llm.structured.StructuredOutputService;
import com.hackathonstarter.llm.tools.ToolCallingService;
import com.hackathonstarter.llm.tools.ToolRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/** Generic, domain-free endpoints that expose the three LLM building blocks. Copy the pattern for real features. */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    public record ChatRequest(String system, @NotBlank String message, String model) {}

    public record ChatResponse(String text, String model, String stopReason, Usage usage) {}

    public record StructuredRequest(String system, @NotBlank String prompt, @NotNull JsonNode schema) {}

    public record StructuredResponse(JsonNode data) {}

    public record ToolChatRequest(String system, @NotBlank String message, List<String> tools, Integer maxIterations) {}

    private final LlmClient llm;
    private final StructuredOutputService structured;
    private final ToolCallingService toolCalling;
    private final ToolRegistry tools;

    public LlmController(LlmClient llm, StructuredOutputService structured, ToolCallingService toolCalling, ToolRegistry tools) {
        this.llm = llm;
        this.structured = structured;
        this.toolCalling = toolCalling;
        this.tools = tools;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        LlmRequest llmRequest = LlmRequest.of(request.system(), request.message());
        if (request.model() != null && !request.model().isBlank()) {
            llmRequest = llmRequest.withModel(request.model());
        }
        LlmResponse response = llm.complete(llmRequest);
        return new ChatResponse(response.text(), response.model(), response.stopReason(), response.usage());
    }

    @PostMapping("/structured")
    public StructuredResponse structured(@Valid @RequestBody StructuredRequest request) {
        return new StructuredResponse(structured.generate(request.system(), request.prompt(), request.schema()));
    }

    @PostMapping("/tool-chat")
    public ToolCallingService.Result toolChat(@Valid @RequestBody ToolChatRequest request) {
        return toolCalling.run(request.system(), request.message(), request.tools(), request.maxIterations());
    }

    @GetMapping("/tools")
    public List<ToolDefinition> listTools() {
        return tools.definitions(null);
    }
}
