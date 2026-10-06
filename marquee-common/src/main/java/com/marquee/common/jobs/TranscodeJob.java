package com.marquee.common.jobs;

import java.util.UUID;

public record TranscodeJob(UUID assetId, String sourceKey) {
}
