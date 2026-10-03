package pl.aniolstroz.config;

import java.time.Instant;
import pl.aniolstroz.contracts.Mode;

public record StatusResponse(Mode mode, String version, Instant startedAt) {
}
