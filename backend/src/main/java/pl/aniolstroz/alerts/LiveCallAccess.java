package pl.aniolstroz.alerts;

import java.util.List;
import java.util.Set;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.StageId;

/**
 * What the alerts package needs from the active call. Implemented by the call package, so alerts never depend on call.
 */
public interface LiveCallAccess {

    /** Alerts of the active call; they reach the database only when the call ends. Empty if no call is active. */
    List<Alert> alerts();

    /**
     * Stops counting the given stages for the call. Returns false if that call is not the active one.
     * This is the one way a call's risk level can drop (DET-05 exception).
     */
    boolean ignoreStages(String callId, Set<StageId> stages);

    /** True while the call with this id is the active one. */
    boolean isActive(String callId);
}
