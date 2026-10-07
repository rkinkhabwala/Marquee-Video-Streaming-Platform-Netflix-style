package com.marquee.api.ingest;

import com.marquee.common.jobs.TranscodeJob;

public interface TranscodeJobPublisher {
    void publish(TranscodeJob job);
}
