package pl.aniolstroz.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult.Usage;
import pl.aniolstroz.audit.AuditStatistics.Sample;
import pl.aniolstroz.config.AppProperties.Audit.Pricing;

/** OBS-04: latency percentiles and cost on BigDecimal, from fixed data and the configured prices. */
class AuditStatisticsTest {

    /** Sonnet 5.5 prices per 1M tokens: input 2, cache read 0.20, output 10. */
    private static final Pricing PRICING = new Pricing(new BigDecimal("2"), new BigDecimal("0.20"), new BigDecimal("10"), new BigDecimal("2.50"));

    private static Sample sample(String callId, long latencyMs, long in, long cacheRead, long out) {
        return new Sample(callId, latencyMs, new Usage(in, cacheRead, out), null, 0);
    }

    private static BigDecimal usd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void costOfOneCallIsTokensTimesPriceDividedByAMillion() {
        // 1000 * 2 + 0 * 0.20 + 100 * 10 = 3000 per million
        assertThat(AuditStatistics.cost(new Usage(1000, 0, 100), PRICING)).isEqualByComparingTo(usd("0.003"));
        // 1200 * 2 + 1000 * 0.20 + 100 * 10 = 3600 per million
        assertThat(AuditStatistics.cost(new Usage(1200, 1000, 100), PRICING)).isEqualByComparingTo(usd("0.0036"));
        // 1000 * 2 + 100 * 10 + 2000 * 2.50 (cache writes) = 8000 per million
        assertThat(AuditStatistics.cost(new Usage(1000, 0, 100, 2000), PRICING)).isEqualByComparingTo(usd("0.008"));
    }

    @Test
    void costIsExactWithoutFloatingPointRounding() {
        // 1 token each: (2 + 0.20 + 10) / 1,000,000
        assertThat(AuditStatistics.cost(new Usage(1, 1, 1), PRICING)).isEqualByComparingTo(usd("0.0000122"));
        assertThat(AuditStatistics.cost(new Usage(0, 0, 0), PRICING)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void costIsComputedFromTheConfiguredPrices() {
        Pricing other = new Pricing(new BigDecimal("1"), new BigDecimal("0.10"), new BigDecimal("5"), new BigDecimal("1.25"));

        assertThat(AuditStatistics.cost(new Usage(1000, 0, 100), other)).isEqualByComparingTo(usd("0.0015"));
    }

    @Test
    void percentilesUseTheNearestRankMethod() {
        List<Long> ten = LongStream.rangeClosed(1, 10).map(i -> i * 100).boxed().toList();

        assertThat(AuditStatistics.percentile(ten, 0.50)).isEqualTo(500);   // rank ceil(5.0) = 5
        assertThat(AuditStatistics.percentile(ten, 0.95)).isEqualTo(1000);  // rank ceil(9.5) = 10
        List<Long> twenty = LongStream.rangeClosed(1, 20).map(i -> i * 10).boxed().toList();
        assertThat(AuditStatistics.percentile(twenty, 0.50)).isEqualTo(100); // rank 10
        assertThat(AuditStatistics.percentile(twenty, 0.95)).isEqualTo(190); // rank 19
        assertThat(AuditStatistics.percentile(List.of(42L), 0.95)).isEqualTo(42);
    }

    @Test
    void percentilesDoNotDependOnTheOrderOfTheInput() {
        AuditSummary summary = AuditStatistics.summarize(List.of(
                sample("c1", 400, 1, 0, 1), sample("c1", 100, 1, 0, 1),
                sample("c2", 300, 1, 0, 1), sample("c2", 200, 1, 0, 1)), 0, PRICING);

        assertThat(summary.latencyP50Ms()).isEqualTo(200);
        assertThat(summary.latencyP95Ms()).isEqualTo(400);
    }

    @Test
    void summaryOnFixedData() {
        List<Sample> samples = List.of(
                sample("c1", 100, 1000, 0, 100),      // 0.0030
                sample("c1", 200, 1200, 1000, 100),   // 0.0036
                sample("c2", 300, 2000, 1500, 200),   // 0.0063
                new Sample("c2", 400, new Usage(0, 0, 0), ClassifierError.TIMEOUT, 0));

        AuditSummary summary = AuditStatistics.summarize(samples, 2, PRICING);

        assertThat(summary.aiCalls()).isEqualTo(4);
        assertThat(summary.conversations()).isEqualTo(2);
        assertThat(summary.mockCallsExcluded()).isEqualTo(2);
        assertThat(summary.latencyP50Ms()).isEqualTo(200);
        assertThat(summary.latencyP95Ms()).isEqualTo(400);
        // total 0.0129 over the 3 calls that returned usage, and over 2 conversations
        assertThat(summary.avgCostPerCallUsd()).isEqualByComparingTo(usd("0.0043"));
        assertThat(summary.avgCostPerConversationUsd()).isEqualByComparingTo(usd("0.00645"));
        assertThat(summary.costNote()).isEqualTo(AuditSummary.COST_NOTE).contains("zapis cache");
        assertThat(summary.pricing()).isEqualTo(new AuditSummary.Pricing(
                new BigDecimal("2"), new BigDecimal("0.20"), new BigDecimal("10"), new BigDecimal("2.50")));
    }

    @Test
    void failedCallsWithoutUsageCountForLatencyButNotForTheAverageCost() {
        AuditSummary summary = AuditStatistics.summarize(List.of(
                sample("c1", 100, 1000, 0, 100),
                new Sample("c1", 2500, new Usage(0, 0, 0), ClassifierError.TIMEOUT, 0)), 0, PRICING);

        assertThat(summary.aiCalls()).isEqualTo(2);
        assertThat(summary.latencyP95Ms()).isEqualTo(2500);
        assertThat(summary.avgCostPerCallUsd()).isEqualByComparingTo(usd("0.003"));
    }

    @Test
    void averagesAreBigDecimalsThatDoNotLoseThePrecisionOfSmallCosts() {
        AuditSummary summary = AuditStatistics.summarize(List.of(sample("c1", 100, 1, 0, 1)), 0, PRICING);

        assertThat(summary.avgCostPerCallUsd()).isEqualByComparingTo(usd("0.000012"));
        assertThat(summary.avgCostPerCallUsd().toPlainString()).doesNotContain("E");
    }

    @Test
    void lateResultsAreCountedSeparatelyAndStayInTheLatencyAndCost() {
        AuditSummary summary = AuditStatistics.summarize(List.of(
                new Sample("c1", 100, new Usage(1000, 0, 100), null, 0, false),
                new Sample("c1", 300, new Usage(1000, 0, 100), null, 0, true)), 0, PRICING);

        assertThat(summary.lateResults()).isEqualTo(1);
        assertThat(summary.rejectedQuotes()).isZero();
        assertThat(summary.aiCalls()).isEqualTo(2);
        assertThat(summary.latencyP95Ms()).isEqualTo(300);
        assertThat(summary.avgCostPerCallUsd()).isEqualByComparingTo(usd("0.003"));
    }

    @Test
    void rejectedQuotesAreSummedAndErrorsAreCountedByCause() {
        AuditSummary summary = AuditStatistics.summarize(List.of(
                new Sample("c1", 100, new Usage(10, 0, 10), null, 2),
                new Sample("c1", 100, new Usage(10, 0, 10), null, 1),
                new Sample("c2", 100, new Usage(0, 0, 0), ClassifierError.TIMEOUT, 0),
                new Sample("c2", 100, new Usage(0, 0, 0), ClassifierError.TIMEOUT, 0),
                new Sample("c2", 100, new Usage(10, 0, 5), ClassifierError.REFUSAL, 0)), 0, PRICING);

        assertThat(summary.rejectedQuotes()).isEqualTo(3);
        assertThat(summary.errorsByCause()).containsOnly(
                java.util.Map.entry(ClassifierError.TIMEOUT, 2), java.util.Map.entry(ClassifierError.REFUSAL, 1));
    }

    @Test
    void nothingToMeasureGivesNoPercentilesOrAveragesNotZeros() {
        AuditSummary summary = AuditStatistics.summarize(List.of(), 3, PRICING);

        assertThat(summary.aiCalls()).isZero();
        assertThat(summary.conversations()).isZero();
        assertThat(summary.mockCallsExcluded()).isEqualTo(3);
        assertThat(summary.latencyP50Ms()).isNull();
        assertThat(summary.latencyP95Ms()).isNull();
        assertThat(summary.avgCostPerCallUsd()).isNull();
        assertThat(summary.avgCostPerConversationUsd()).isNull();
        assertThat(summary.errorsByCause()).isEmpty();
        assertThat(summary.rejectedQuotes()).isZero();
    }
}
