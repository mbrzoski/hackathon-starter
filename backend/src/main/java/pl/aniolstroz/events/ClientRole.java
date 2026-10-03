package pl.aniolstroz.events;

import java.util.Locale;
import java.util.Optional;

/** Role a /ws/events client connects with. */
public enum ClientRole {
    SENIOR, FAMILY, AUDIT;

    static Optional<ClientRole> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "senior" -> Optional.of(SENIOR);
            case "family" -> Optional.of(FAMILY);
            case "audit" -> Optional.of(AUDIT);
            default -> Optional.empty();
        };
    }
}
