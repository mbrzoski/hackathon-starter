package pl.aniolstroz.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** TST-04: package dependencies (BE-05) and no {@code synchronized} in ai and stt (CC-03). */
@AnalyzeClasses(packages = "pl.aniolstroz", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule aiDoesNotDependOnRisk = noClasses()
            .that().resideInAPackage("..ai..")
            .should().dependOnClassesThat().resideInAPackage("..risk..")
            .allowEmptyShould(true);

    /** The deterministic core stays free of orchestration: call depends on risk and alerts, never the reverse. */
    @ArchTest
    static final ArchRule riskDoesNotDependOnCallOrAlerts = noClasses()
            .that().resideInAPackage("..risk..")
            .should().dependOnClassesThat().resideInAnyPackage("..call..", "..alerts..");

    /** The AI layer only returns evidence: it knows nothing of calls, alerts, demo scenarios or the event bus. */
    @ArchTest
    static final ArchRule aiDoesNotDependOnCallAlertsDemoOrEvents = noClasses()
            .that().resideInAPackage("..ai..")
            .should().dependOnClassesThat().resideInAnyPackage("..call..", "..alerts..", "..demo..", "..events..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule alertsDoNotDependOnCall = noClasses()
            .that().resideInAPackage("..alerts..")
            .should().dependOnClassesThat().resideInAPackage("..call..");

    @ArchTest
    static final ArchRule noSynchronizedMethodsInAiAndStt = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..ai..", "..stt..")
            .should(new com.tngtech.archunit.lang.ArchCondition<JavaMethod>("not be synchronized") {
                @Override
                public void check(JavaMethod method, com.tngtech.archunit.lang.ConditionEvents events) {
                    boolean sync = method.getModifiers().contains(JavaModifier.SYNCHRONIZED);
                    events.add(new com.tngtech.archunit.lang.SimpleConditionEvent(
                            method, !sync, method.getFullName() + (sync ? " is synchronized" : " is not synchronized")));
                }
            })
            .allowEmptyShould(true);

    private static final Pattern SYNCHRONIZED = Pattern.compile("\\bsynchronized\\b");
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** ArchUnit cannot see synchronized blocks in bytecode, so the keyword is also searched in the sources. */
    @Test
    void synchronizedKeywordAbsentFromAiAndSttSources() throws IOException {
        for (String pkg : List.of("ai", "stt")) {
            Path dir = Path.of("src/main/java/pl/aniolstroz", pkg);
            assertThat(dir).isDirectory();
            try (Stream<Path> files = Files.walk(dir)) {
                List<Path> offenders = files
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> {
                            try {
                                String code = COMMENTS.matcher(Files.readString(p)).replaceAll(" ");
                                return SYNCHRONIZED.matcher(code).find();
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .toList();
                assertThat(offenders).as("files using synchronized in package %s", pkg).isEmpty();
            }
        }
    }
}
