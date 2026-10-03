package pl.aniolstroz.alerts;

import java.util.List;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.Decision;

/** An alert and every decision made about it, oldest decision first. */
public record AlertHistoryEntry(Alert alert, List<Decision> decisions) {
}
