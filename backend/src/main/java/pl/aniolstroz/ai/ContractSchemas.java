package pl.aniolstroz.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * The response schema for structured output, taken from components/schemas/StageHits of the contract (AI-03): the
 * contract stays the single source of truth. The API needs a self-contained schema, so every
 * {@code $ref} to another component is replaced by that component, and the documentation keywords title and
 * description (which carry no rule) are dropped to save tokens.
 */
final class ContractSchemas {

    private static final String CONTRACT = "contract/openapi.yaml";
    private static final String REF_PREFIX = "#/components/schemas/";

    private static volatile Map<String, Object> stageHits;

    private ContractSchemas() {
    }

    static Map<String, Object> stageHits() {
        Map<String, Object> cached = stageHits;
        if (cached == null) {
            cached = Collections.unmodifiableMap(load("StageHits"));
            stageHits = cached;
        }
        return cached;
    }

    private static Map<String, Object> load(String name) {
        try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
            JsonNode schemas = new YAMLMapper().readTree(in).path("components").path("schemas");
            Object resolved = resolve(schemas.get(name), schemas, false);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) resolved;
            removeBackendOnlyStages(map);
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + CONTRACT, e);
        }
    }

    /** FAMILY_KEYWORD is set by the backend only: the model's schema must not offer it (rule 5: family words stay home). */
    @SuppressWarnings("unchecked")
    private static void removeBackendOnlyStages(Map<String, Object> schema) {
        Map<String, Object> hit = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) schema
                .get("properties")).get("stage_hits")).get("items");
        Map<String, Object> stage = (Map<String, Object>) ((Map<String, Object>) hit.get("properties")).get("stage");
        List<Object> names = new java.util.ArrayList<>((List<Object>) stage.get("enum"));
        names.remove("FAMILY_KEYWORD");
        stage.put("enum", names);
    }

    /** @param propertyNames true when the node is a {@code properties} map, whose keys are names and not keywords */
    private static Object resolve(JsonNode node, JsonNode schemas, boolean propertyNames) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.get("$ref").asText();
                if (!ref.startsWith(REF_PREFIX) || !schemas.has(ref.substring(REF_PREFIX.length()))) {
                    throw new IllegalStateException("Unresolvable $ref in " + CONTRACT + ": " + ref);
                }
                return resolve(schemas.get(ref.substring(REF_PREFIX.length())), schemas, false);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (!propertyNames && (key.equals("title") || key.equals("description"))) {
                    return;
                }
                map.put(key, resolve(entry.getValue(), schemas, !propertyNames && key.equals("properties")));
            });
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new java.util.ArrayList<>();
            node.forEach(item -> list.add(resolve(item, schemas, false)));
            return list;
        }
        return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(node, new TypeReference<Object>() { });
    }
}
