package pl.aniolstroz.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult.Usage;
import pl.aniolstroz.config.AppProperties.Audit.Pricing;

/**
 * Pure arithmetic of the audit summary (OBS-04): nearest-rank percentiles and cost on {@link BigDecimal}, so there is
 * no floating-point rounding in money.
 */
final class AuditStatistics {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int SCALE = 12;

    /** One real AI call. */
    record Sample(String callId, long latencyMs, Usage usage, ClassifierError error, int rejectedHits, boolean late) {

        Sample(String callId, long latencyMs, Usage usage, ClassifierError error, int rejectedHits) {
            this(callId, latencyMs, usage, error, rejectedHits, false);
        }

        boolean hasUsage() {
            return usage.inputTokens() + usage.cacheReadInputTokens() + usage.outputTokens()
                    + usage.cacheCreationInputTokens() > 0;
        }
    }

    private AuditStatistics() {
    }

    /**
     * Latency covers every real call, failures included. Cost averages cover the calls that returned usage: a failed
     * call without usage has an unknown cost, and counting it as zero would make the average look cheaper than it is.
     *
     * @param mockExcluded how many mock calls were left out of {@code samples}
     */
    static AuditSummary summarize(List<Sample> samples, int mockExcluded, Pricing pricing) {
        List<Long> latencies = samples.stream().map(Sample::latencyMs).sorted().toList();
        List<Sample> costed = samples.stream().filter(Sample::hasUsage).toList();
        Set<String> conversations = new HashSet<>();
        samples.forEach(s -> conversations.add(s.callId()));
        Set<String> costedConversations = new HashSet<>();
        costed.forEach(s -> costedConversations.add(s.callId()));

        BigDecimal total = costed.stream().map(s -> cost(s.usage(), pricing)).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<ClassifierError, Integer> errors = new EnumMap<>(ClassifierError.class);
        samples.stream().filter(s -> s.error() != null).forEach(s -> errors.merge(s.error(), 1, Integer::sum));

        return new AuditSummary(
                samples.size(),
                conversations.size(),
                mockExcluded,
                latencies.isEmpty() ? null : percentile(latencies, 0.50),
                latencies.isEmpty() ? null : percentile(latencies, 0.95),
                average(total, costed.size()),
                average(total, costedConversations.size()),
                samples.stream().mapToInt(Sample::rejectedHits).sum(),
                (int) samples.stream().filter(Sample::late).count(),
                Collections.unmodifiableMap(errors),
                new AuditSummary.Pricing(pricing.inputPerMillionUsd(), pricing.cacheReadPerMillionUsd(),
                        pricing.outputPerMillionUsd(), pricing.cacheCreationPerMillionUsd()),
                AuditSummary.COST_NOTE);
    }

    /** Cost in USD of one call: tokens times the price per million, divided by a million. */
    static BigDecimal cost(Usage usage, Pricing pricing) {
        BigDecimal perMillion = pricing.inputPerMillionUsd().multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(pricing.cacheReadPerMillionUsd().multiply(BigDecimal.valueOf(usage.cacheReadInputTokens())))
                .add(pricing.outputPerMillionUsd().multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(pricing.cacheCreationPerMillionUsd().multiply(BigDecimal.valueOf(usage.cacheCreationInputTokens())));
        return perMillion.divide(MILLION, SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Nearest rank: the value at position ceil(q * n) of the sorted values. */
    static long percentile(List<Long> values, double q) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(q * sorted.size() - 1e-9);
        return sorted.get(Math.min(sorted.size(), Math.max(1, rank)) - 1);
    }

    private static BigDecimal average(BigDecimal total, int count) {
        if (count == 0) {
            return null;
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        return average.signum() == 0 ? BigDecimal.ZERO : average;
    }
}
