package com.hackathonstarter.llm.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/** Example tool showing the ToolHandler contract. Delete freely; nothing depends on it. */
@Component
public class CurrentTimeTool implements ToolHandler {

    private final ObjectMapper mapper;

    public CurrentTimeTool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "Returns the current date and time (ISO-8601) in an optional IANA timezone, default UTC.";
    }

    @Override
    public JsonNode inputSchema() {
        var schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties").putObject("timezone")
                .put("type", "string")
                .put("description", "IANA timezone id, e.g. Europe/Warsaw");
        return schema;
    }

    @Override
    public String execute(JsonNode input) {
        String zone = input.path("timezone").asText("UTC");
        return ZonedDateTime.now(ZoneId.of(zone)).toString();
    }
}
