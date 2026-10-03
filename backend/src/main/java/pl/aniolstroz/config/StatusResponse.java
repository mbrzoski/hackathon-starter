package pl.aniolstroz.config;

import java.time.Instant;

public record StatusResponse(Mode mode, String version, Instant startedAt) {
}
