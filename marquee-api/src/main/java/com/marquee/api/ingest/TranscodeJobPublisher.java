package com.marquee.api.ingest;

public interface TranscodeJobPublisher {
    void publish(TranscodeJobMessage job);
}
