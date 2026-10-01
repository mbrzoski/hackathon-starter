package com.hackathonstarter.llm.tools;

import com.hackathonstarter.llm.model.ToolDefinition;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new LinkedHashMap<>();

    public ToolRegistry(List<ToolHandler> toolHandlers) {
        toolHandlers.forEach(h -> {
            if (handlers.putIfAbsent(h.name(), h) != null) {
                throw new IllegalStateException("Duplicate tool name: " + h.name());
            }
        });
    }

    public Optional<ToolHandler> find(String name) {
        return Optional.ofNullable(handlers.get(name));
    }

    /** @param names tool names to expose; null or empty means all registered tools */
    public List<ToolDefinition> definitions(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return handlers.values().stream().map(ToolHandler::definition).toList();
        }
        return names.stream()
                .map(n -> find(n).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + n)))
                .map(ToolHandler::definition)
                .toList();
    }
}
