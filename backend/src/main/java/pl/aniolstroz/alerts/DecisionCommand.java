package pl.aniolstroz.alerts;

import java.util.Set;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.StageId;

/** What a person sent about an alert. {@code ignoredStages} is never null; it may be empty. */
public record DecisionCommand(Actor actor, DecisionType decision, Set<StageId> ignoredStages) {

    public DecisionCommand {
        ignoredStages = ignoredStages == null ? Set.of() : Set.copyOf(ignoredStages);
    }
}
