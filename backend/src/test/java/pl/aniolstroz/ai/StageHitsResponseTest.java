package pl.aniolstroz.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageId;

/**
 * CON-07: the hand-written response record, not a generated model, is what Claude's answer is mapped to, and its JSON
 * must satisfy components/schemas/StageHits. The schema sent to the API is that same contract schema.
 */
class StageHitsResponseTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private static JsonNode contract() throws IOException {
        try (InputStream in = new ClassPathResource("contract/openapi.yaml").getInputStream()) {
            return new YAMLMapper().readTree(in);
        }
    }

    private static Schema stageHitsSchema() throws IOException {
        JsonNode contract = contract();
        ObjectNode root = ((ObjectNode) contract).objectNode();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("$ref", "#/components/schemas/StageHits");
        root.set("components", contract.get("components"));
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(root);
    }

    private static StageHitsResponse sample() {
        return new StageHitsResponse(List.of(
                new StageHitsResponse.Hit(StageId.SECRECY_DEMAND, "s9", "o tej rozmowie nikomu pani nie mówi",
                        SpeakerRole.CALLER),
                new StageHitsResponse.Hit(StageId.MONEY_REQUEST, "s11", "wypłacić wszystko", SpeakerRole.SENIOR)));
    }

    @Test
    void itsJsonPassesTheContractSchema() throws Exception {
        JsonNode json = mapper.valueToTree(sample());

        List<Error> errors = stageHitsSchema().validate(json);

        assertThat(errors).isEmpty();
        assertThat(json.get("stage_hits").get(0).fieldNames()).toIterable()
                .containsExactly("stage", "segment_id", "quote", "speaker_role");
        assertThat(json.get("stage_hits").get(1).get("speaker_role").asText()).isEqualTo("senior");
    }

    @Test
    void emptyAnswerPassesTheContractSchema() throws Exception {
        assertThat(stageHitsSchema().validate(mapper.valueToTree(new StageHitsResponse(List.of())))).isEmpty();
    }

    @Test
    void itReadsWhatTheContractSchemaAllows() throws Exception {
        String json = """
                {"stage_hits":[{"stage":"PAYMENT_CHANNEL","segment_id":"s3","quote":"kod BLIK","speaker_role":"background"}]}""";

        StageHitsResponse parsed = mapper.readValue(json, StageHitsResponse.class);

        assertThat(stageHitsSchema().validate(mapper.readTree(json))).isEmpty();
        assertThat(parsed.stageHits()).containsExactly(new StageHitsResponse.Hit(
                StageId.PAYMENT_CHANNEL, "s3", "kod BLIK", SpeakerRole.BACKGROUND));
    }

    @Test
    void theSchemaWouldRejectWhatTheRecordCannotRead() throws Exception {
        String json = """
                {"stage_hits":[{"stage":"NOT_A_STAGE","segment_id":"s3","quote":"x","speaker_role":"caller"}]}""";

        assertThat(stageHitsSchema().validate(mapper.readTree(json))).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSchemaSentToTheApiIsSelfContainedAndFollowsStructuredOutputRules() {
        Map<String, Object> schema = ContractSchemas.stageHits();
        String text = schema.toString();

        assertThat(text).doesNotContain("$ref").doesNotContain("minLength").doesNotContain("minimum")
                .doesNotContain("maxLength").doesNotContain("pattern");
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat((List<Object>) schema.get("required")).containsExactly("stage_hits");
        Map<?, ?> hit = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("stage_hits")).get("items");
        assertThat(hit.get("additionalProperties")).isEqualTo(false);
        assertThat((List<Object>) hit.get("required")).containsExactlyInAnyOrder("stage", "segment_id", "quote", "speaker_role");
        Map<?, ?> hitProps = (Map<?, ?>) hit.get("properties");
        assertThat((List<Object>) ((Map<?, ?>) hitProps.get("stage")).get("enum"))
                .containsExactlyElementsOf(java.util.Arrays.stream(StageId.values()).map(Enum::name).toList());
        assertThat((List<Object>) ((Map<?, ?>) hitProps.get("speaker_role")).get("enum"))
                .containsExactly("caller", "senior", "background", "unclear");
    }

    @Test
    void everyObjectInTheSentSchemaForbidsAdditionalProperties() {
        List<Object> objects = new ArrayList<>();
        collectObjects(ContractSchemas.stageHits(), objects);

        assertThat(objects).isNotEmpty().allSatisfy(o ->
                assertThat(((Map<?, ?>) o).get("additionalProperties")).isEqualTo(false));
    }

    private static void collectObjects(Object node, List<Object> out) {
        if (node instanceof Map<?, ?> map) {
            if ("object".equals(map.get("type"))) {
                out.add(map);
            }
            map.values().forEach(v -> collectObjects(v, out));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collectObjects(v, out));
        }
    }
}
