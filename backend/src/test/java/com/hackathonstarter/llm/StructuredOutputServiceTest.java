package com.hackathonstarter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathonstarter.llm.model.ContentBlock;
import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import com.hackathonstarter.llm.model.Usage;
import com.hackathonstarter.llm.structured.StructuredOutputException;
import com.hackathonstarter.llm.structured.StructuredOutputService;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredOutputServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient llm = mock(LlmClient.class);
    private final StructuredOutputService service = new StructuredOutputService(llm, mapper);

    record Person(String name, int age) {}

    private JsonNode schema() throws Exception {
        return mapper.readTree("""
                {"type":"object","required":["name","age"],
                 "properties":{"name":{"type":"string"},"age":{"type":"integer"}}}""");
    }

    private LlmResponse toolCall(String json) throws Exception {
        return new LlmResponse("m", List.of(new ContentBlock.ToolUse("id1", "structured_output", mapper.readTree(json))),
                "tool_use", new Usage(1, 1));
    }

    @Test
    void returnsValidOutputAsTypedObject() throws Exception {
        when(llm.complete(any())).thenReturn(toolCall("{\"name\":\"Ada\",\"age\":36}"));

        Person p = service.generate(null, "who?", schema(), Person.class);

        assertThat(p).isEqualTo(new Person("Ada", 36));
    }

    @Test
    void retriesOnceWithValidationErrorsThenSucceeds() throws Exception {
        when(llm.complete(any()))
                .thenReturn(toolCall("{\"name\":\"Ada\"}"))
                .thenReturn(toolCall("{\"name\":\"Ada\",\"age\":36}"));

        JsonNode out = service.generate(null, "who?", schema());

        assertThat(out.path("age").asInt()).isEqualTo(36);
        verify(llm, times(2)).complete(any(LlmRequest.class));
    }

    @Test
    void failsWithErrorsWhenStillInvalidAfterRetries() throws Exception {
        when(llm.complete(any())).thenReturn(toolCall("{\"name\":\"Ada\",\"age\":\"old\"}"));

        assertThatThrownBy(() -> service.generate(null, "who?", schema()))
                .isInstanceOfSatisfying(StructuredOutputException.class,
                        e -> assertThat(e.getErrors()).isNotEmpty());
    }

    @Test
    void failsWhenModelDoesNotCallTheTool() throws Exception {
        when(llm.complete(any())).thenReturn(new LlmResponse("m", List.of(new ContentBlock.Text("hi")), "end_turn", Usage.ZERO));

        assertThatThrownBy(() -> service.generate(null, "who?", schema()))
                .isInstanceOf(StructuredOutputException.class);
    }

    @Test
    void rejectsNonObjectRootSchema() throws Exception {
        JsonNode bad = mapper.readTree("{\"type\":\"string\"}");

        assertThatThrownBy(() -> service.generate(null, "x", bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mockClientProducesSchemaConformingOutputOffline() throws Exception {
        var offline = new StructuredOutputService(new MockLlmClient(mapper, "mock-model"), mapper);

        Person p = offline.generate(null, "anything", schema(), Person.class);

        assertThat(p).isEqualTo(new Person("mock", 0));
    }
}
