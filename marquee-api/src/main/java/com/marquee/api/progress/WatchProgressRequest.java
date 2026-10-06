package com.marquee.api.progress;

import jakarta.validation.constraints.Min;

public record WatchProgressRequest(@Min(0) Integer positionSeconds) {
}
