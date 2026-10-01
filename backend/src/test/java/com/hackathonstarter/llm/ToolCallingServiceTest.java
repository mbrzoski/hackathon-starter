package com.hackathonstarter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.Usage;
import com.hackathonstarter.llm.tools.CurrentTimeTool;
import com.hackathonstarter.llm.tools.ToolCallingService;
import com.hackathonstarter.llm.tools.ToolHandler;
import com.hackathonstarter.llm.tools.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallingServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient llm = mock(LlmClient.class);

    private ToolHandler failingTool() {
        return new ToolHandler() {
            public String name() { return "explode"; }
            public String description() { return "always fails"; }
            public JsonNode inputSchema() { return mapper.createObjectNode().put("type", "object"); }
            public String execute(JsonNode input) { throw new IllegalStateException("kaboom"); }
        };
    }

    private LlmResponse toolUse(String name) {
        return new LlmResponse("m",
                List.of(new ContentBlock.ToolUse("tu_" + name, name, mapper.createObjectNode())),
                "tool_use", new Usage(2, 3));
    }

    private LlmResponse text(String t) {
        return new LlmResponse("m", List.of(new ContentBlock.Text(t)), "end_turn", new Usage(1, 1));
    }

    @Test
    void executesToolThenReturnsFinalText() {
        var registry = new ToolRegistry(List.of(new CurrentTimeTool(mapper)));
        var service = new ToolCallingService(llm, registry);
        when(llm.complete(any())).thenReturn(toolUse("get_current_time")).thenReturn(text("It is now."));

        var result = service.run(null, "time?", null, null);

        assertThat(result.text()).isEqualTo("It is now.");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).error()).isFalse();
        assertThat(result.usage()).isEqualTo(new Usage(3, 4));
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void reportsToolFailureToTheModelInsteadOfCrashing() {
        var service = new ToolCallingService(llm, new ToolRegistry(List.of(failingTool())));
        when(llm.complete(any())).thenReturn(toolUse("explode")).thenReturn(text("Sorry."));

        var result = service.run(null, "go", List.of("explode"), null);

        assertThat(result.steps().get(0).error()).isTrue();
        assertThat(result.steps().get(0).output()).contains("kaboom");
        assertThat(result.text()).isEqualTo("Sorry.");
    }

    @Test
    void stopsAtIterationLimit() {
        var service = new ToolCallingService(llm, new ToolRegistry(List.of(failingTool())));
        when(llm.complete(any())).thenReturn(toolUse("explode"));

        var result = service.run(null, "go", null, 2);

        assertThat(result.truncated()).isTrue();
        assertThat(result.steps()).hasSize(2);
    }

    @Test
    void unknownRequestedToolNameIsRejected() {
        var service = new ToolCallingService(llm, new ToolRegistry(List.of()));

        assertThatThrownBy(() -> service.run(null, "go", List.of("nope"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateToolNamesFailFast() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(failingTool(), failingTool())))
                .isInstanceOf(IllegalStateException.class);
    }
}
