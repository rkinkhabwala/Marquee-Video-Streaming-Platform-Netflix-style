package com.marquee.api.ingest;

import java.time.Instant;
import java.util.List;

public record AssetStatusResponse(Long assetId,
                                  Long titleId,
                                  String status,
                                  String sourceKey,
                                  String masterPlaylistKey,
                                  Integer durationSeconds,
                                  String errorMessage,
                                  List<Attempt> attempts) {
    public record Attempt(int attempt, String status, Instant startedAt, Instant finishedAt, String logTail) {
    }
}
