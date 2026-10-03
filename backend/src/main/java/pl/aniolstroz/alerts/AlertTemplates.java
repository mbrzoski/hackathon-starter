package pl.aniolstroz.alerts;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/** The pre-written alert texts from {@code templates/alerts.pl.yml} (DET-04). */
public final class AlertTemplates {

    private static final String BUNDLED = "templates/alerts.pl.yml";

    /** {@code shortText} is one sentence for reading aloud; {@code advice} follows it. */
    public record Template(String shortText, String advice) {
    }

    private record Document(Map<String, Template> templates) {
    }

    private final Map<String, Template> templates;

    private AlertTemplates(Map<String, Template> templates) {
        this.templates = templates;
    }

    public static AlertTemplates bundled() {
        try (InputStream in = new ClassPathResource(BUNDLED).getInputStream()) {
            return parse(in, BUNDLED);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + BUNDLED, e);
        }
    }

    /** Fails at startup if any template the selector can return is missing or blank. */
    public static AlertTemplates parse(InputStream yaml, String name) {
        Document document;
        try {
            document = new YAMLMapper().readValue(yaml, Document.class);
        } catch (IOException e) {
            throw new IllegalStateException(name + ": cannot parse alert templates (" + e.getMessage() + ")", e);
        }
        Map<String, Template> templates = document.templates() == null ? Map.of() : document.templates();
        for (String id : TemplateSelector.ALL_IDS) {
            Template template = templates.get(id);
            if (template == null || isBlank(template.shortText()) || isBlank(template.advice())) {
                throw new IllegalStateException(name + ": template '" + id + "' is missing or has an empty text");
            }
        }
        return new AlertTemplates(Map.copyOf(templates));
    }

    public Template get(String templateId) {
        Template template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template " + templateId);
        }
        return template;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
