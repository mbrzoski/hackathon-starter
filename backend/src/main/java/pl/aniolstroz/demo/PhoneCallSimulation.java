package pl.aniolstroz.demo;

import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import pl.aniolstroz.contracts.EventEnvelope.PhoneCallEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.PhoneCall;
import pl.aniolstroz.events.EventBus;

/**
 * The simulated incoming phone call of the demo (family panel). Switching it on or off tells every screen
 * ({@code phone.call}): the senior sees who is "calling", the listening device listens only while it is on. State is
 * in memory and starts off. Nothing is dialled and nothing is stored (rule 3). The mode is SCRIPTED: it is staged.
 */
@Service
public class PhoneCallSimulation {

    /** A made-up number, never a real person's. */
    static final String NUMBER = "+48 600 100 200";

    private final EventBus eventBus;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean active;

    PhoneCallSimulation(EventBus eventBus, Clock clock) {
        this.eventBus = eventBus;
        this.clock = clock;
    }

    public PhoneCall current() {
        lock.lock();
        try {
            return new PhoneCall(active, NUMBER);
        } finally {
            lock.unlock();
        }
    }

    /** Sets the state and tells everybody, also when it did not change (idempotent, cheap). */
    public PhoneCall set(boolean on) {
        lock.lock();
        try {
            active = on;
            PhoneCall state = new PhoneCall(on, NUMBER);
            eventBus.publish(new PhoneCallEvent(Mode.SCRIPTED, clock.instant(), state));
            return state;
        } finally {
            lock.unlock();
        }
    }
}
