package com.marquee.api.recsys;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** One event in recsys' public JSON contract ({@code POST /v1/events}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecsysEvent(
        String eventId,
        String userId,
        String itemId,
        String domain,
        String eventType,
        Double value,
        // ISO-8601 on the wire regardless of how the ObjectMapper is configured.
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant eventTs,
        String sessionId,
        Context context,
        Media media,
        String searchQueryId) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Context(String device, String surface) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Media(Long positionMs, Long durationMs) {
    }
}
