package pl.aniolstroz.demo;

import java.util.List;

/** A scenario file does not match components/schemas/Scenario. The message names the file and the fields. */
public class ScenarioValidationException extends RuntimeException {

    public ScenarioValidationException(String fileName, List<String> problems) {
        super("Scenario file '" + fileName + "' is invalid: " + String.join("; ", problems));
    }

    public ScenarioValidationException(String fileName, String problem, Throwable cause) {
        super("Scenario file '" + fileName + "' is invalid: " + problem, cause);
    }
}
