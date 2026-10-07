package com.marquee.transcoder.service;

import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;

public interface TranscodeEventPublisher {
    void publish(TranscodeEvent event);

    void scheduleRetry(TranscodeJob job);

    void deadLetter(TranscodeJob job);
}
