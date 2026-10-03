package pl.aniolstroz.audit;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.CallState;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

/** Every request and response is checked against contracts/openapi.yaml (TST-03). */
@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    private static final String SPEC = "contract/openapi.yaml";
    private static final long WAIT_MS = 5_000;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CallService calls;
    @Autowired
    AuditService audit;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanSlate() {
        calls.end();
        jdbc.sql("DELETE FROM audit_records").update();
        jdbc.sql("DELETE FROM audit_calls").update();
    }

    private void say(CallState call, String text) {
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, text, true, SpeakerLabel.B, null));
    }

    /** The mock AI answers asynchronously: wait until it has been audited for the given last segment. */
    private void awaitAuditUpTo(String callId, String lastSegment) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<AuditRecord> records = audit.callAudit(callId).orElse(List.of());
            if (records.stream().anyMatch(r -> r.segmentRange().endsWith("-" + lastSegment))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("no audit record up to " + lastSegment + " for " + callId);
    }

    /** A call that plays the first three segments of scenario 01 through the pipeline with the mock AI. */
    private CallState callWithThreeSegments() throws InterruptedException {
        CallState call = calls.start(Mode.SCRIPTED, "01-fake-police-classic");
        say(call, "Dzień dobry, czy rozmawiam z panią Zofią Testową?");
        say(call, "Tak, tak, a kto mówi?");
        say(call, "Mówi podkomisarz Jan Fikcyjny, policja, komenda numer zero zero zero. Dzwonię w ważnej sprawie.");
        awaitAuditUpTo(call.callId(), "s3");
        return call;
    }

    private static AuditEntry realEntry(String callId, long latencyMs, ClassifierResult.Usage usage,
            ClassifierError error, int rejected) {
        List<StageHit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < rejected; i++) {
            hits.add(new StageHit(StageId.MONEY_REQUEST, "s" + (i + 1), "zmyślony", SpeakerRole.CALLER,
                    HitSource.LLM, false));
        }
        return new AuditEntry(callId, Mode.SCRIPTED, new ClassifierResult("s1-s2", "claude-sonnet-5-5", "low",
                error == null ? "{}" : null, hits, usage, latencyMs, error == null ? "end_turn" : null, error),
                RiskLevel.NONE, RiskLevel.NONE, List.of());
    }

    @Test
    void auditOfALiveCallShowsTheAiCallWithEverythingOfOBS03() throws Exception {
        CallState call = callWithThreeSegments();

        mockMvc.perform(get("/api/calls/" + call.callId() + "/audit"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$[0].callId").value(call.callId()))
                .andExpect(jsonPath("$[0].mode").value("SCRIPTED"))
                .andExpect(jsonPath("$[0].model").value("mock"))
                .andExpect(jsonPath("$[0].usage.inputTokens").value(0))
                .andExpect(jsonPath("$[0].segmentRange").isNotEmpty())
                .andExpect(jsonPath("$[0].levelBefore").isNotEmpty())
                .andExpect(jsonPath("$[0].levelAfter").isNotEmpty())
                .andExpect(jsonPath("$[0].textCleared").value(false))
                .andExpect(jsonPath("$[0].rawOutput").exists())
                .andExpect(jsonPath("$[*].keywordHits").exists());
    }

    @Test
    void afterACallWithoutAnAlertTheAuditHasNoTextButStillAllNumbers() throws Exception {
        CallState call = callWithThreeSegments();

        calls.end();

        // Several AI calls were audited (after s1, s2, s3); every one of them lost its text, none lost its numbers.
        mockMvc.perform(get("/api/calls/" + call.callId() + "/audit"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$[*].textCleared", everyItem(is(true))))
                .andExpect(jsonPath("$[*].rawOutput").doesNotExist())
                .andExpect(jsonPath("$[*].hits[*].quote").doesNotExist())
                .andExpect(jsonPath("$[*].keywordHits[*].quote").doesNotExist())
                .andExpect(jsonPath("$[*].hits[*].stage", hasItem("AUTHORITY_CLAIM")))
                .andExpect(jsonPath("$[*].hits[*].validated", hasItem(true)))
                .andExpect(jsonPath("$[*].keywordHits[*].stage", hasItem("AUTHORITY_CLAIM")))
                .andExpect(jsonPath("$[*].latencyMs", everyItem(isA(Number.class))))
                .andExpect(jsonPath("$[*].usage.outputTokens", everyItem(isA(Number.class))));
    }

    @Test
    void callListShowsTheCallWithItsEndMaxLevelAndNumberOfAiCalls() throws Exception {
        CallState call = callWithThreeSegments();
        calls.end();

        mockMvc.perform(get("/api/calls"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$[0].callId").value(call.callId()))
                .andExpect(jsonPath("$[0].mode").value("SCRIPTED"))
                .andExpect(jsonPath("$[0].startedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].endedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].maxLevel").value("low"))
                .andExpect(jsonPath("$[0].aiCalls").isNumber());
    }

    @Test
    void anActiveCallHasNoEndYet() throws Exception {
        calls.start(Mode.LIVE);

        mockMvc.perform(get("/api/calls"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$[0].endedAt").doesNotExist())
                .andExpect(jsonPath("$[0].aiCalls").value(0));
    }

    @Test
    void unknownCallIsProblemDetail404() throws Exception {
        mockMvc.perform(get("/api/calls/no-such-call/audit"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void limitOutsideTheContractRangeIsProblemDetail400() throws Exception {
        for (String limit : List.of("0", "201", "abc")) {
            mockMvc.perform(get("/api/calls").param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void summaryOnFixedDataWithTheConfiguredPrices() throws Exception {
        audit.record(realEntry("c1", 100, new ClassifierResult.Usage(1000, 0, 100), null, 0));
        audit.record(realEntry("c1", 200, new ClassifierResult.Usage(1200, 1000, 100), null, 2));
        audit.record(realEntry("c2", 300, new ClassifierResult.Usage(2000, 1500, 200), null, 0));
        audit.record(realEntry("c2", 400, new ClassifierResult.Usage(0, 0, 0), ClassifierError.TIMEOUT, 0));

        mockMvc.perform(get("/api/audit/summary"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.aiCalls").value(4))
                .andExpect(jsonPath("$.conversations").value(2))
                .andExpect(jsonPath("$.latencyP50Ms").value(200))
                .andExpect(jsonPath("$.latencyP95Ms").value(400))
                .andExpect(jsonPath("$.avgCostPerCallUsd").value(0.0043))
                .andExpect(jsonPath("$.avgCostPerConversationUsd").value(0.00645))
                .andExpect(jsonPath("$.rejectedQuotes").value(2))
                .andExpect(jsonPath("$.errorsByCause.TIMEOUT").value(1))
                .andExpect(jsonPath("$.pricing.inputPerMillionUsd").value(2))
                .andExpect(jsonPath("$.pricing.cacheReadPerMillionUsd").value(0.2))
                .andExpect(jsonPath("$.pricing.outputPerMillionUsd").value(10))
                .andExpect(jsonPath("$.costNote").value(AuditSummary.COST_NOTE));
    }

    @Test
    void summaryOfAnEmptyAuditIsValidAndHasNoInventedNumbers() throws Exception {
        mockMvc.perform(get("/api/audit/summary"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC))
                .andExpect(jsonPath("$.aiCalls").value(0))
                .andExpect(jsonPath("$.latencyP50Ms").doesNotExist())
                .andExpect(jsonPath("$.avgCostPerCallUsd").doesNotExist())
                .andExpect(jsonPath("$.costNote").isNotEmpty());
    }

    @Test
    void costsAreWrittenAsPlainNumbersNeverInScientificNotation() throws Exception {
        audit.record(realEntry("c1", 100, new ClassifierResult.Usage(1, 0, 1), null, 0));

        String body = mockMvc.perform(get("/api/audit/summary")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        assertThat(body).contains("\"avgCostPerCallUsd\":0.000012").doesNotContain("E-");
    }
}
