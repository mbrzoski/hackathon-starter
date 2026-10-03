package pl.aniolstroz.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.core.io.ClassPathResource;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.QuoteNormalizer;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * Keyword layer (DET-03): the baseline that also works when the AI is down. Patterns come from
 * {@code risk/keywords.pl.yml} and are matched against each sentence after {@link QuoteNormalizer}, so missing
 * Polish diacritics from the STT do not matter. A hit quotes the original sentence, not the normalized one.
 */
public final class KeywordDetector {

    private static final String BUNDLED = "risk/keywords.pl.yml";
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private final Map<StageId, List<Pattern>> patterns;

    private KeywordDetector(Map<StageId, List<Pattern>> patterns) {
        this.patterns = patterns;
    }

    public static KeywordDetector bundled() {
        try (InputStream in = new ClassPathResource(BUNDLED).getInputStream()) {
            return fromYaml(in, BUNDLED);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + BUNDLED, e);
        }
    }

    /** @param name file name used in error messages */
    public static KeywordDetector fromYaml(InputStream yaml, String name) {
        Map<String, List<String>> raw;
        try {
            raw = new YAMLMapper().readValue(yaml, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalStateException(name + ": cannot parse keyword dictionary (" + e.getMessage() + ")", e);
        }
        Map<StageId, List<Pattern>> compiled = new EnumMap<>(StageId.class);
        raw.forEach((stageName, regexes) -> {
            StageId stage = parseStage(name, stageName);
            List<Pattern> list = new ArrayList<>();
            for (String regex : regexes) {
                list.add(compile(name, stage, regex));
            }
            compiled.put(stage, List.copyOf(list));
        });
        return new KeywordDetector(compiled);
    }

    /** Stages that have at least one pattern. */
    public Set<StageId> stagesWithPatterns() {
        Set<StageId> stages = EnumSet.noneOf(StageId.class);
        patterns.forEach((stage, list) -> {
            if (!list.isEmpty()) {
                stages.add(stage);
            }
        });
        return stages;
    }

    /** One hit per stage found in the segment, quoting the first sentence in which the stage matches. */
    public List<StageHit> detect(TranscriptSegment segment) {
        Map<StageId, StageHit> found = new EnumMap<>(StageId.class);
        for (String raw : SENTENCE_END.split(segment.text())) {
            String sentence = raw.strip();
            if (sentence.isEmpty()) {
                continue;
            }
            String normalized = QuoteNormalizer.normalize(sentence);
            patterns.forEach((stage, list) -> {
                if (!found.containsKey(stage) && list.stream().anyMatch(p -> p.matcher(normalized).find())) {
                    found.put(stage, new StageHit(stage, segment.segId(), sentence, SpeakerRole.UNCLEAR,
                            HitSource.KEYWORDS, true));
                }
            });
        }
        return List.copyOf(found.values());
    }

    private static StageId parseStage(String file, String stageName) {
        try {
            return StageId.valueOf(stageName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(file + ": unknown stage '" + stageName + "'", e);
        }
    }

    private static Pattern compile(String file, StageId stage, String regex) {
        try {
            return Pattern.compile(regex, FLAGS);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException(file + ": invalid pattern '" + regex + "' for " + stage, e);
        }
    }
}
