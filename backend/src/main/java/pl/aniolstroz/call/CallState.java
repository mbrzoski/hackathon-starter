package pl.aniolstroz.call;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.QuoteNormalizer;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * State of one call. Every read and write of the mutable fields happens under the call's own lock (CC-01, CC-03).
 * The transcript lives only in memory (DAT-01). The risk level only grows (DET-05).
 */
public final class CallState {

    private record HitKey(StageId stage, String segId, HitSource source) {
    }

    private final String callId;
    private final Mode mode;
    private final String scenarioId;
    private final Instant startedAt;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<TranscriptSegment> transcript = new ArrayList<>();
    private final List<StageHit> hits = new ArrayList<>();
    private final Set<HitKey> hitKeys = new HashSet<>();
    private final Set<HitKey> provisionalKeys = new HashSet<>();
    private final List<Alert> alerts = new ArrayList<>();
    private final Set<StageId> ignoredStages = EnumSet.noneOf(StageId.class);
    private int segmentCount;
    private RiskLevel level = RiskLevel.NONE;
    private RiskLevel maxLevel = RiskLevel.NONE;
    private boolean ended;

    CallState(String callId, Mode mode, String scenarioId, Instant startedAt) {
        this.callId = callId;
        this.mode = mode;
        this.scenarioId = scenarioId;
        this.startedAt = startedAt;
    }

    public String callId() {
        return callId;
    }

    public Mode mode() {
        return mode;
    }

    /** The demo scenario this call plays, or null. */
    public String scenarioId() {
        return scenarioId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Copy of the final segments kept so far. */
    public List<TranscriptSegment> transcript() {
        return locked(() -> List.copyOf(transcript));
    }

    /** Copy of the stage hits, without duplicates (key: stage, segment and source). */
    public List<StageHit> hits() {
        return locked(() -> List.copyOf(hits));
    }

    public RiskLevel level() {
        return locked(() -> level);
    }

    /** The highest level this call ever had. Unlike {@link #level()} it does not drop when stages are ignored. */
    public RiskLevel maxLevel() {
        return locked(() -> maxLevel);
    }

    /** Copy of the alerts raised so far, one per level. */
    public List<Alert> alerts() {
        return locked(() -> List.copyOf(alerts));
    }

    /** Stages the family asked not to count for this call. */
    public Set<StageId> ignoredStages() {
        return locked(() -> Set.copyOf(ignoredStages));
    }

    public boolean hadAlert() {
        return locked(() -> !alerts.isEmpty());
    }

    /** Removes the whole transcript from memory (DAT-01). */
    public void clearTranscript() {
        lock.lock();
        try {
            transcript.clear();
        } finally {
            lock.unlock();
        }
    }

    /** Keeps only the given segments (the excerpt of an alerted call). */
    void retainTranscript(List<TranscriptSegment> excerpt) {
        lock.lock();
        try {
            transcript.clear();
            transcript.addAll(excerpt);
        } finally {
            lock.unlock();
        }
    }

    ReentrantLock lock() {
        return lock;
    }

    /**
     * Id for a segment about to be added. A final segment takes the next number; an interim one gets the number
     * the next final segment will take, so interim and final of one utterance share an id. Caller holds the lock.
     */
    String segmentIdFor(boolean isFinal) {
        return "s" + (isFinal ? ++segmentCount : segmentCount + 1);
    }

    /** Caller must hold the lock. */
    void keep(TranscriptSegment segment) {
        transcript.add(segment);
    }

    /** Returns false if the hit was already known. Caller must hold the lock. */
    boolean addHit(StageHit hit) {
        return addHit(hit, false);
    }

    /**
     * Returns false if the hit was already known. A provisional hit comes from an interim segment: it quotes text the
     * recognizer may still change, so {@link #settleProvisionalHits} checks it when the final segment arrives.
     * Caller must hold the lock.
     */
    boolean addHit(StageHit hit, boolean provisional) {
        HitKey key = new HitKey(hit.stage(), hit.segId(), hit.source());
        if (!hitKeys.add(key)) {
            return false;
        }
        hits.add(hit);
        if (provisional) {
            provisionalKeys.add(key);
        }
        return true;
    }

    /**
     * Settles the provisional hits of the segment that has just become final. If the final text gives the very same
     * hits ({@code finalHits}), they simply become permanent. Otherwise they are removed, so the hits of the final text
     * can take their place (a stage the final text no longer has stays out). Returns true if hits were removed. The
     * level never drops because of it (DET-05) and an alert already raised stays. Caller must hold the lock.
     */
    boolean settleProvisionalHits(String segId, List<StageHit> finalHits) {
        List<HitKey> keys = provisionalKeys.stream().filter(k -> k.segId().equals(segId)).toList();
        if (keys.isEmpty()) {
            return false;
        }
        provisionalKeys.removeAll(keys);
        List<StageHit> provisional = hits.stream()
                .filter(h -> keys.contains(new HitKey(h.stage(), h.segId(), h.source()))).toList();
        if (evidenceOf(provisional).equals(evidenceOf(finalHits))) {
            return false;
        }
        hitKeys.removeAll(keys);
        hits.removeAll(provisional);
        return true;
    }

    /** What a hit says, compared the way quotes are validated: the stage and the normalized quote (CON-05). */
    private static Set<List<Object>> evidenceOf(List<StageHit> stageHits) {
        return stageHits.stream()
                .map(h -> List.<Object>of(h.stage(), QuoteNormalizer.normalize(h.quote())))
                .collect(java.util.stream.Collectors.toSet());
    }

    /** Caller must hold the lock. */
    void setLevel(RiskLevel newLevel) {
        level = newLevel;
        if (newLevel.compareTo(maxLevel) > 0) {
            maxLevel = newLevel;
        }
    }

    /** Caller must hold the lock. */
    void recordAlert(Alert alert) {
        alerts.add(alert);
    }

    /** Returns true if at least one of the stages was not ignored yet. Caller must hold the lock. */
    boolean addIgnoredStages(Set<StageId> stages) {
        return ignoredStages.addAll(stages);
    }

    /** Caller must hold the lock. */
    boolean hasAlertAt(RiskLevel alertLevel) {
        return alerts.stream().anyMatch(a -> a.level() == alertLevel);
    }

    /** Caller must hold the lock. */
    boolean hasAlertWithTemplate(RiskLevel alertLevel, String templateId) {
        return alerts.stream().anyMatch(a -> a.level() == alertLevel && a.templateId().equals(templateId));
    }

    /** Caller must hold the lock. */
    boolean isEnded() {
        return ended;
    }

    /** Caller must hold the lock. */
    void markEnded() {
        ended = true;
    }

    private <T> T locked(java.util.function.Supplier<T> read) {
        lock.lock();
        try {
            return read.get();
        } finally {
            lock.unlock();
        }
    }
}
