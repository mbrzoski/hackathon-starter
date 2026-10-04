package pl.aniolstroz.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import pl.aniolstroz.ai.CallSnapshot;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.ai.ClaudeStageClassifier;
import pl.aniolstroz.ai.MockStageClassifier;
import pl.aniolstroz.ai.StageClassifier;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.Scenario;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.risk.KeywordDetector;
import pl.aniolstroz.risk.QuoteValidator;
import pl.aniolstroz.risk.RiskEngine;

/**
 * Evaluation runner (BE-12, TST-05, architecture 6.9). Runs the 12 written scenarios in three variants, keywords only,
 * AI only and both, through the same code as the application: {@link KeywordDetector}, the classifier called after
 * every final segment with the transcript so far (AI-02), {@link QuoteValidator} and {@link RiskEngine} at the
 * standard sensitivity. It checks the expected <em>properties</em> of each scenario (level, stages that must and must
 * not be found, verbatim quotes) and writes a table of which variant got them right: no percentages, no score.
 *
 * <p>With {@code ANTHROPIC_API_KEY} it calls the real model (cost: about one call per segment). Without it the AI
 * column is the MOCK classifier and the report says so in its title. Never run in tests (TST-02).
 *
 * <pre>
 *   make eval                         # writes docs/eval/wyniki-ewaluacji.md and docs/eval/wyniki-ewaluacji.json
 *   ... EvalRunner --write-mocks      # also stores the model's answers as MOCK answers for scenarios without one
 * </pre>
 */
public final class EvalRunner {

    private static final Set<StageId> PRESSURE_OR_MONEY = EnumSet.of(StageId.AUTHORITY_CLAIM, StageId.URGENT_THREAT,
            StageId.SECRECY_DEMAND, StageId.ISOLATION, StageId.MONEY_REQUEST, StageId.PAYMENT_CHANNEL,
            StageId.REMOTE_ACCESS, StageId.PERSONAL_DATA_REQUEST);

    enum Variant { KEYWORDS, AI, BOTH }

    /** What one variant concluded about one scenario. */
    record Outcome(RiskLevel level, Integer firstAlertSegment, Set<StageId> stages, boolean levelOk, boolean mustHitOk,
            boolean mustNotHitOk, List<StageId> missed, List<StageId> wronglyHit) {

        boolean allOk() {
            return levelOk && mustHitOk && mustNotHitOk;
        }
    }

    /** The AI side of one scenario: answers, rejected quotes, cost numbers. */
    record AiRun(List<StageHit> validatedHits, int rejectedQuotes, int calls, int failures, List<Long> latenciesMs,
            long inputTokens, long cacheReadTokens, long cacheWriteTokens, long outputTokens,
            List<Map<String, Object>> mockSteps) {
    }

    record ScenarioResult(String scenarioId, String title, Scenario.Expected expected, Map<Variant, Outcome> outcomes,
            AiRun ai) {
    }

    private EvalRunner() {
    }

    public static void main(String[] args) throws Exception {
        boolean writeMocks = List.of(args).contains("--write-mocks");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String key = System.getenv("ANTHROPIC_API_KEY");
        boolean real = key != null && !key.isBlank();
        String model = System.getenv().getOrDefault("APP_CLAUDE_MODEL", "claude-sonnet-5-5");
        long timeoutMs = Long.parseLong(System.getenv().getOrDefault("APP_CLAUDE_TIMEOUT_MS", "8000"));
        StageClassifier classifier = real
                ? ClaudeStageClassifier.create(key, null, model, 1024, Duration.ofMillis(timeoutMs), Clock.systemUTC(), mapper)
                : new MockStageClassifier(mapper);

        List<Scenario> scenarios = new ScenarioRepository(mapper).all();
        KeywordDetector keywords = KeywordDetector.bundled();
        System.out.printf("Evaluating %d scenarios, AI: %s%n", scenarios.size(), real ? model : "MOCK (no API key)");

        List<ScenarioResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ScenarioResult>> futures = new ArrayList<>();
            for (Scenario scenario : scenarios) {
                futures.add(pool.submit(() -> run(scenario, keywords, classifier)));
            }
            for (Future<ScenarioResult> f : futures) {
                ScenarioResult r = f.get();
                results.add(r);
                System.out.printf("  %-28s keywords %s  ai %s  both %s%n", r.scenarioId(),
                        mark(r.outcomes().get(Variant.KEYWORDS)), mark(r.outcomes().get(Variant.AI)),
                        mark(r.outcomes().get(Variant.BOTH)));
            }
        }

        Path out = Path.of(System.getProperty("eval.out", "../docs/eval"));
        Files.createDirectories(out);
        Files.writeString(out.resolve("wyniki-ewaluacji.md"), markdown(results, real ? model : null));
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("wyniki-ewaluacji.json").toFile(),
                Map.of("date", LocalDate.now().toString(), "ai", real ? model : "MOCK", "results", results));
        System.out.println("Written: " + out.toAbsolutePath().normalize().resolve("wyniki-ewaluacji.md"));
        if (writeMocks && real) {
            writeMocks(results, mapper, Path.of("src/main/resources/mocks"));
        }
    }

    private static String mark(Outcome o) {
        return o.allOk() ? "ok  " : "FAIL";
    }

    static ScenarioResult run(Scenario scenario, KeywordDetector keywords, StageClassifier classifier) {
        List<TranscriptSegment> transcript = new ArrayList<>();
        List<StageHit> keywordHits = new ArrayList<>();
        Map<String, StageHit> aiHits = new LinkedHashMap<>();
        Integer[] firstAlert = new Integer[Variant.values().length];
        int rejected = 0;
        int calls = 0;
        int failures = 0;
        List<Long> latencies = new ArrayList<>();
        long in = 0;
        long cacheRead = 0;
        long cacheWrite = 0;
        long outTokens = 0;
        List<Map<String, Object>> steps = new ArrayList<>();
        int previousAiHits = 0;

        for (int i = 0; i < scenario.segments().size(); i++) {
            Scenario.Segment s = scenario.segments().get(i);
            long t = i * 1000L;
            TranscriptSegment segment = new TranscriptSegment("eval-" + scenario.scenarioId(), "s" + (i + 1), t, t + 900,
                    s.text(), true, s.speaker(), null);
            transcript.add(segment);
            keywordHits.addAll(keywords.detect(segment));

            ClassifierResult result = classifier.classify(new CallSnapshot("eval-" + scenario.scenarioId(), Mode.SCRIPTED,
                    scenario.scenarioId(), transcript));
            calls++;
            latencies.add(result.latencyMs());
            in += result.usage().inputTokens();
            cacheRead += result.usage().cacheReadInputTokens();
            cacheWrite += result.usage().cacheCreationInputTokens();
            outTokens += result.usage().outputTokens();
            if (result.failed()) {
                failures++;
            } else {
                for (StageHit hit : QuoteValidator.validate(result.hits(), transcript)) {
                    if (!hit.validated()) {
                        rejected++;
                    } else if (hit.stage() != StageId.FAMILY_KEYWORD) {
                        aiHits.putIfAbsent(hit.stage() + "|" + hit.segId(), hit);
                    }
                }
            }
            if (aiHits.size() > previousAiHits) {
                previousAiHits = aiHits.size();
                steps.add(Map.of("afterSegment", i + 1, "stage_hits", aiHits.values().stream().map(EvalRunner::asMock).toList()));
            }
            for (Variant v : Variant.values()) {
                if (firstAlert[v.ordinal()] == null
                        && level(hitsOf(v, keywordHits, aiHits)).compareTo(RiskLevel.MEDIUM) >= 0) {
                    firstAlert[v.ordinal()] = i + 1;
                }
            }
        }

        Map<Variant, Outcome> outcomes = new LinkedHashMap<>();
        for (Variant v : Variant.values()) {
            outcomes.put(v, judge(scenario.expected(), hitsOf(v, keywordHits, aiHits), firstAlert[v.ordinal()]));
        }
        return new ScenarioResult(scenario.scenarioId(), scenario.title(), scenario.expected(), outcomes,
                new AiRun(List.copyOf(aiHits.values()), rejected, calls, failures, latencies, in, cacheRead, cacheWrite,
                        outTokens, steps));
    }

    private static Map<String, Object> asMock(StageHit hit) {
        return Map.of("stage", hit.stage().name(), "segment_id", hit.segId(), "quote", hit.quote(),
                "speaker_role", hit.speakerRole().name().toLowerCase());
    }

    private static List<StageHit> hitsOf(Variant v, List<StageHit> keywordHits, Map<String, StageHit> aiHits) {
        return switch (v) {
            case KEYWORDS -> keywordHits;
            case AI -> List.copyOf(aiHits.values());
            case BOTH -> {
                List<StageHit> all = new ArrayList<>(keywordHits);
                all.addAll(aiHits.values());
                yield all;
            }
        };
    }

    private static RiskLevel level(List<StageHit> hits) {
        return RiskEngine.computeLevel(hits, Sensitivity.STANDARD).level();
    }

    /**
     * Properties, not scores. Level: a scam must reach the expected level, a harmless call must stay at or below it
     * (no alert). Stages: those attributed to the caller (not to the senior or the background, DET-02) count.
     */
    static Outcome judge(Scenario.Expected expected, List<StageHit> hits, Integer firstAlert) {
        RiskLevel level = level(hits);
        boolean scam = expected.maxLevel().compareTo(RiskLevel.MEDIUM) >= 0;
        boolean levelOk = scam ? level.compareTo(expected.maxLevel()) >= 0 : level.compareTo(expected.maxLevel()) <= 0;
        Set<StageId> fromCaller = EnumSet.noneOf(StageId.class);
        RiskEngine.countedHits(hits).stream()
                .filter(h -> h.speakerRole() != SpeakerRole.SENIOR && h.speakerRole() != SpeakerRole.BACKGROUND)
                .filter(h -> PRESSURE_OR_MONEY.contains(h.stage()))
                .forEach(h -> fromCaller.add(h.stage()));
        List<StageId> missed = expected.mustHitStages().stream().filter(s -> !fromCaller.contains(s)).toList();
        List<StageId> wrong = expected.mustNotHitStages().stream().filter(fromCaller::contains).toList();
        return new Outcome(level, firstAlert, fromCaller, levelOk, missed.isEmpty(), wrong.isEmpty(), missed, wrong);
    }

    static String markdown(List<ScenarioResult> results, String model) {
        StringBuilder md = new StringBuilder();
        md.append("# Ewaluacja: słowa kluczowe, AI i oba razem\n\n");
        md.append("Wygenerowane przez `EvalRunner` (`make eval`) dnia ").append(LocalDate.now()).append(". ");
        md.append(model == null
                ? "**AI = MOCK (brak klucza API): kolumna AI pokazuje gotowe odpowiedzi, nie prawdziwy model.**"
                : "AI: `" + model + "`, prawdziwe wywołania, jedno po każdym segmencie (jak w aplikacji).");
        md.append("\n\nKażdy scenariusz ma oczekiwane **właściwości**, nie wynik punktowy (TST-05). ")
                .append("✓ = wszystkie spełnione, ✗ = coś nie (powód w nawiasie). Poziom: oszustwo musi osiągnąć ")
                .append("oczekiwany poziom, zwykła rozmowa nie może dać ostrzeżenia. Etapy liczone są tylko dla rozmówcy ")
                .append("(nie seniora i nie tła). Czułość: standardowa. „Alert w” = segment, w którym poziom pierwszy raz ")
                .append("osiągnął średni.\n\n");
        md.append("| Scenariusz | Oczekiwane | Słowa kluczowe | AI | Oba |\n|---|---|---|---|---|\n");
        for (ScenarioResult r : results) {
            md.append("| ").append(r.scenarioId()).append(" ").append(r.title()).append(" | ")
                    .append(r.expected().maxLevel().name().toLowerCase()).append(" | ")
                    .append(cell(r.outcomes().get(Variant.KEYWORDS))).append(" | ")
                    .append(cell(r.outcomes().get(Variant.AI))).append(" | ")
                    .append(cell(r.outcomes().get(Variant.BOTH))).append(" |\n");
        }
        long ok = results.stream().filter(r -> r.outcomes().get(Variant.BOTH).allOk()).count();
        md.append("\nScenariusze ze wszystkimi właściwościami spełnionymi: słowa kluczowe ")
                .append(count(results, Variant.KEYWORDS)).append(", AI ").append(count(results, Variant.AI))
                .append(", oba ").append(ok).append(" (na ").append(results.size()).append(").\n");

        if (model != null) {
            List<Long> latencies = results.stream().flatMap(r -> r.ai().latenciesMs().stream()).sorted().toList();
            int calls = results.stream().mapToInt(r -> r.ai().calls()).sum();
            int failures = results.stream().mapToInt(r -> r.ai().failures()).sum();
            int rejected = results.stream().mapToInt(r -> r.ai().rejectedQuotes()).sum();
            long in = results.stream().mapToLong(r -> r.ai().inputTokens()).sum();
            long read = results.stream().mapToLong(r -> r.ai().cacheReadTokens()).sum();
            long write = results.stream().mapToLong(r -> r.ai().cacheWriteTokens()).sum();
            long out = results.stream().mapToLong(r -> r.ai().outputTokens()).sum();
            double cost = (in * 2.0 + read * 0.20 + write * 2.50 + out * 10.0) / 1_000_000;
            md.append("\n## Zmierzone liczby AI\n\n| Miara | Wartość |\n|---|---|\n")
                    .append("| Wywołania | ").append(calls).append(" |\n")
                    .append("| Błędy (timeout, odmowa itp.) | ").append(failures).append(" |\n")
                    .append("| Odrzucone cytaty (QuoteValidator) | ").append(rejected).append(" |\n")
                    .append("| Opóźnienie p50 | ").append(percentile(latencies, 0.50)).append(" ms |\n")
                    .append("| Opóźnienie p95 | ").append(percentile(latencies, 0.95)).append(" ms |\n")
                    .append("| Tokeny: wejście / odczyt cache / zapis cache / wyjście | ").append(in).append(" / ")
                    .append(read).append(" / ").append(write).append(" / ").append(out).append(" |\n")
                    .append(String.format(java.util.Locale.ROOT, "| Koszt całego przebiegu (cennik z konfiguracji: 2 / 0,20 / 2,50 / 10 USD za 1M) | %.4f USD |%n", cost))
                    .append(String.format(java.util.Locale.ROOT, "| Średni koszt na rozmowę | %.4f USD |%n", cost / results.size()));
            md.append("\nPercentyle metodą najbliższej rangi (D-36). Opóźnienie liczone od wysłania do odpowiedzi API, ")
                    .append("bez czasu rozpoznawania mowy. Koszt zależy od cache promptu: przebieg z zimnym cache płaci za zapis ")
                    .append("rubryki i transkrypcji do cache, kolejny w ciągu kilku minut czyta je taniej.\n");
        }
        md.append("\n## Szczegóły\n\n");
        for (ScenarioResult r : results) {
            md.append("- **").append(r.scenarioId()).append("**: ");
            for (Variant v : Variant.values()) {
                Outcome o = r.outcomes().get(v);
                md.append(v.name().toLowerCase()).append(" → ").append(o.level().name().toLowerCase())
                        .append(" ").append(o.stages()).append("; ");
            }
            md.append("odrzucone cytaty AI: ").append(r.ai().rejectedQuotes()).append("\n");
        }
        return md.toString();
    }

    private static long count(List<ScenarioResult> results, Variant v) {
        return results.stream().filter(r -> r.outcomes().get(v).allOk()).count();
    }

    private static String cell(Outcome o) {
        StringBuilder c = new StringBuilder(o.allOk() ? "✓ " : "✗ ").append(o.level().name().toLowerCase());
        if (o.firstAlertSegment() != null) {
            c.append(", alert w s").append(o.firstAlertSegment());
        }
        List<String> why = new ArrayList<>();
        if (!o.levelOk()) {
            why.add("poziom");
        }
        if (!o.missed().isEmpty()) {
            why.add("brak " + o.missed());
        }
        if (!o.wronglyHit().isEmpty()) {
            why.add("niepotrzebnie " + o.wronglyHit());
        }
        if (!why.isEmpty()) {
            c.append(" (").append(String.join("; ", why)).append(")");
        }
        return c.toString();
    }

    private static long percentile(List<Long> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int rank = (int) Math.ceil(q * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    /** Stores the model's answers as MOCK answers, only for scenarios that have none yet (hand-written ones stay). */
    private static void writeMocks(List<ScenarioResult> results, ObjectMapper mapper, Path dir) throws IOException {
        for (ScenarioResult r : results) {
            Path file = dir.resolve(r.scenarioId() + ".json");
            if (Files.exists(file) || r.ai().mockSteps().isEmpty()) {
                continue;
            }
            Map<String, Object> mock = new LinkedHashMap<>();
            mock.put("scenarioId", r.scenarioId());
            mock.put("description", "Recorded answers of the real model (" + LocalDate.now()
                    + ", EvalRunner --write-mocks), only quotes that QuoteValidator accepted. Used in MOCK mode.");
            mock.put("steps", r.ai().mockSteps());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), mock);
            System.out.println("Mock written: " + file);
        }
    }
}
