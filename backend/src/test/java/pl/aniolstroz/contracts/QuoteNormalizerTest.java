package pl.aniolstroz.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CON-05: the backend and the frontend normalise quotes the same way. Both read the shared vectors in
 * contracts/test-vectors/normalize.json, so a change in either implementation fails its own tests.
 */
class QuoteNormalizerTest {

    /** Maven runs tests with the backend directory as the working directory. */
    private static final Path VECTORS = Path.of("..", "contracts", "test-vectors", "normalize.json");

    @Test
    void sharedVectorsFileHasCasesToCheck() throws IOException {
        assertThat(vectors()).isNotEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void normalizesLikeTheSharedVectors(String input, String expected) {
        assertThat(QuoteNormalizer.normalize(input)).isEqualTo(expected);
    }

    static Stream<Arguments> vectors() throws IOException {
        JsonNode root = new ObjectMapper().readTree(VECTORS.toFile());
        List<Arguments> cases = new ArrayList<>();
        for (JsonNode vector : root.get("vectors")) {
            cases.add(Arguments.of(vector.get("input").asText(), vector.get("expected").asText()));
        }
        return cases.stream();
    }
}
