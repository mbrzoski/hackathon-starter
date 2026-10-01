package com.hackathonstarter.common;

import java.time.Instant;
import java.util.List;

/** Uniform error body returned by every failing REST call. Frontend mocks should mirror this shape. */
public record ApiError(Instant timestamp, int status, String error, String message, String path, List<String> details) {}
