package pl.aniolstroz.demo;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.CallState;
import pl.aniolstroz.ai.StageClassifier;
import pl.aniolstroz.call.NoActiveCallException;
import pl.aniolstroz.config.Trace;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.Scenario;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * Plays a written scenario as a call in SCRIPTED mode: no audio, no STT, final segments only. Playback runs on
 * a virtual thread; each segment waits {@code delayMs / speed} before it is published. Segment text is not logged.
 */
@Service
public class ScriptedPlayer {

    private static final Logger log = LoggerFactory.getLogger(ScriptedPlayer.class);
    private static final Duration STOP_JOIN_TIMEOUT = Duration.ofSeconds(2);

    private final ScenarioRepository scenarios;
    private final CallService calls;
    private final Sleeper sleeper;
    private final StageClassifier classifier;

    private final ReentrantLock lock = new ReentrantLock();
    private Thread playback;

    ScriptedPlayer(ScenarioRepository scenarios, CallService calls, Sleeper sleeper, StageClassifier classifier) {
        this.scenarios = scenarios;
        this.calls = calls;
        this.sleeper = sleeper;
        this.classifier = classifier;
    }

    /**
     * Starts the call and the playback thread.
     *
     * @throws ScenarioNotFoundException if there is no such scenario
     * @throws pl.aniolstroz.call.CallAlreadyActiveException if a call is already running (BE-04)
     */
    public void start(String scenarioId, double speed) {
        if (!(speed > 0)) {
            throw new IllegalArgumentException("speed must be positive");
        }
        Scenario scenario = scenarios.find(scenarioId).orElseThrow(() -> new ScenarioNotFoundException(scenarioId));
        lock.lock();
        try {
            // SCRIPTED promises a real AI; with canned answers the call must say MOCK.
            CallState call = calls.start(classifier.isMock() ? Mode.MOCK : Mode.SCRIPTED, scenarioId);
            Trace.flow("demo | scenario {} starts, speed {}x, {} segments, call={}, ai={}", scenarioId, speed,
                    scenario.segments().size(), Trace.id(call.callId()), classifier.isMock() ? "canned answers (MOCK)" : "real model");
            playback = Thread.ofVirtual().name("scripted-player-" + scenarioId)
                    .unstarted(() -> play(call, scenario, speed));
            playback.start();
        } finally {
            lock.unlock();
        }
    }

    /** Interrupts the playback and waits for it to finish, so the call is already ended when this returns. */
    public void stop() {
        Thread running;
        lock.lock();
        try {
            running = playback;
        } finally {
            lock.unlock();
        }
        if (running == null) {
            return;
        }
        running.interrupt();
        try {
            if (!running.join(STOP_JOIN_TIMEOUT)) {
                log.warn("Scripted playback did not stop within {}", STOP_JOIN_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void play(CallState call, Scenario scenario, double speed) {
        try {
            long timelineMs = 0;
            for (Scenario.Segment segment : scenario.segments()) {
                long waitMs = Math.round(segment.delayMs() / speed);
                sleeper.sleep(Duration.ofMillis(waitMs));
                timelineMs += waitMs;
                calls.addSegment(new TranscriptSegment(call.callId(), "s0", timelineMs, timelineMs,
                        segment.text(), true, segment.speaker(), null));
            }
        } catch (InterruptedException e) {
            // stop() was called: leave quietly, the call is ended below
        } catch (NoActiveCallException e) {
            // the call was ended from elsewhere: nothing left to play
        } finally {
            calls.end(call.callId());
            lock.lock();
            try {
                if (playback == Thread.currentThread()) {
                    playback = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
