package pl.aniolstroz.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** CON-01, CON-06: the control messages of /ws/audio are the schema AudioControl, and the record reads exactly that. */
class AudioControlTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private static JsonNode contract() throws IOException {
        try (InputStream in = new ClassPathResource("contract/openapi.yaml").getInputStream()) {
            return new YAMLMapper().readTree(in);
        }
    }

    private static Schema schema() throws IOException {
        JsonNode contract = contract();
        ObjectNode root = ((ObjectNode) contract).objectNode();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("$ref", "#/components/schemas/AudioControl");
        root.set("components", contract.get("components"));
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(root);
    }

    @Test
    void everyCommandOfTheRecordPassesTheContractSchemaAndIsReadBack() throws Exception {
        for (AudioControl.Command command : AudioControl.Command.values()) {
            String json = mapper.writeValueAsString(new AudioControl(command));

            assertThat(schema().validate(mapper.readTree(json))).as(json).isEmpty();
            assertThat(mapper.readValue(json, AudioControl.class).type()).isEqualTo(command);
        }
    }

    @Test
    void theWireFormatIsLowerCase() throws Exception {
        assertThat(mapper.writeValueAsString(new AudioControl(AudioControl.Command.START)))
                .isEqualTo("{\"type\":\"start\"}");
    }

    @Test
    void whatTheSchemaRejectsTheRecordRejectsToo() throws Exception {
        for (String json : new String[] {"{\"type\":\"explode\"}", "{\"type\":\"start\",\"extra\":1}", "{}"}) {
            assertThat(schema().validate(mapper.readTree(json))).as(json).isNotEmpty();
        }
        assertThatThrownBy(() -> mapper.readValue("{\"type\":\"explode\"}", AudioControl.class))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"type\":\"start\",\"extra\":1}", AudioControl.class))
                .isInstanceOf(IOException.class);
    }

    @Test
    void theContractDescribesBothWebSocketsAndTheCloseCodesOfTheAudioOne() throws Exception {
        JsonNode websockets = contract().get("x-websockets");

        assertThat(websockets.get("/ws/audio").at("/client-to-server/text/$ref").asText())
                .isEqualTo("#/components/schemas/AudioControl");
        assertThat(websockets.get("/ws/events").at("/server-to-client/text/$ref").asText())
                .isEqualTo("#/components/schemas/EventEnvelope");
        assertThat(websockets.get("/ws/audio").get("close-codes").fieldNames())
                .toIterable().containsExactlyInAnyOrder("1000", "1008", "1009", "1011");
    }
}
