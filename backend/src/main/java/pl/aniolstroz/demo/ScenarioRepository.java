package pl.aniolstroz.demo;

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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.Scenario;

/**
 * Demo scenarios from {@code resources/scenarios/*.json}, loaded at startup. Each file is validated against
 * {@code components/schemas/Scenario} of the contract (CON-06); an invalid file stops startup with a message that
 * names the file and the field.
 */
@Component
public class ScenarioRepository {

    private static final String CONTRACT = "contract/openapi.yaml";
    private static final String SCENARIO_REF = "#/components/schemas/Scenario";

    private final List<Scenario> scenarios;

    @Autowired
    public ScenarioRepository(ObjectMapper mapper) {
        this(loadAll(mapper, bundledFiles()));
    }

    ScenarioRepository(List<Scenario> scenarios) {
        this.scenarios = List.copyOf(scenarios);
    }

    static ScenarioRepository fromResources(ObjectMapper mapper, Resource... files) {
        return new ScenarioRepository(loadAll(mapper, List.of(files)));
    }

    /** All scenarios in file-name order. */
    public List<Scenario> all() {
        return scenarios;
    }

    public Optional<Scenario> find(String scenarioId) {
        return scenarios.stream().filter(s -> s.scenarioId().equals(scenarioId)).findFirst();
    }

    private static List<Resource> bundledFiles() {
        try {
            return List.of(new PathMatchingResourcePatternResolver().getResources("classpath:scenarios/*.json"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list scenario files", e);
        }
    }

    private static List<Scenario> loadAll(ObjectMapper mapper, List<Resource> files) {
        Schema schema = scenarioSchema();
        List<Resource> sorted = files.stream()
                .sorted(Comparator.comparing(f -> String.valueOf(f.getFilename())))
                .toList();
        List<Scenario> loaded = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Resource file : sorted) {
            String name = String.valueOf(file.getFilename());
            Scenario scenario = load(mapper, schema, file, name);
            if (!ids.add(scenario.scenarioId())) {
                throw new ScenarioValidationException(name,
                        List.of("duplicate scenarioId '" + scenario.scenarioId() + "'"));
            }
            loaded.add(scenario);
        }
        return loaded;
    }

    private static Scenario load(ObjectMapper mapper, Schema schema, Resource file, String name) {
        JsonNode tree;
        try (InputStream in = file.getInputStream()) {
            tree = mapper.readTree(in);
        } catch (IOException e) {
            throw new ScenarioValidationException(name, "not readable JSON (" + e.getClass().getSimpleName() + ")", e);
        }
        List<Error> errors = schema.validate(tree);
        if (!errors.isEmpty()) {
            throw new ScenarioValidationException(name, errors.stream()
                    .map(e -> e.getInstanceLocation() + ": " + e.getMessage())
                    .sorted()
                    .toList());
        }
        try {
            return mapper.treeToValue(tree, Scenario.class);
        } catch (IOException e) {
            throw new ScenarioValidationException(name, "cannot be mapped to Scenario (" + e.getMessage() + ")", e);
        }
    }

    /** The Scenario schema, resolved inside the whole contract so its $refs to Mode, StageId and so on work. */
    private static Schema scenarioSchema() {
        try (InputStream in = new ClassPathResource(CONTRACT).getInputStream()) {
            JsonNode contract = new YAMLMapper().readTree(in);
            ObjectNode root = ((ObjectNode) contract).objectNode();
            root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
            root.put("$ref", SCENARIO_REF);
            root.set("components", contract.get("components"));
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + CONTRACT, e);
        }
    }
}
