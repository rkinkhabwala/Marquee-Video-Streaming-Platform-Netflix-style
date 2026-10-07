package com.marquee.transcoder.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marquee.common.jobs.TranscodeEvent;
import com.marquee.common.jobs.TranscodeJob;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TranscodeConsumerTest {
    private final TranscodeService transcodeService = mock(TranscodeService.class);
    private final TranscodeEventPublisher events = mock(TranscodeEventPublisher.class);
    private final TranscodeConsumer consumer = new TranscodeConsumer(transcodeService, events, 3);

    @Test
    void reportsStartAndSuccess() throws Exception {
        TranscodeJob job = new TranscodeJob(7L, "raw/7/source.mp4", 1);
        when(transcodeService.transcode(job)).thenReturn(new TranscodeService.Result("hls/7/master.m3u8", 5));

        consumer.consume(job);

        InOrder order = inOrder(events);
        order.verify(events).publish(TranscodeEvent.started(7L, 1));
        order.verify(events).publish(TranscodeEvent.succeeded(7L, 1, 5, "hls/7/master.m3u8"));
        verify(events, never()).scheduleRetry(any());
    }

    @Test
    void schedulesRetryWithNextAttemptBeforeLastAttempt() throws Exception {
        TranscodeJob job = new TranscodeJob(7L, "raw/7/source.mp4", 2);
        when(transcodeService.transcode(job)).thenThrow(new IllegalStateException("ffmpeg exited with 1"));

        consumer.consume(job);

        verify(events).publish(TranscodeEvent.failed(7L, 2, "ffmpeg exited with 1", true));
        verify(events).scheduleRetry(new TranscodeJob(7L, "raw/7/source.mp4", 3));
        verify(events, never()).deadLetter(any());
    }

    @Test
    void deadLettersAfterLastAttempt() throws Exception {
        TranscodeJob job = new TranscodeJob(7L, "raw/7/source.mp4", 3);
        when(transcodeService.transcode(job)).thenThrow(new IllegalStateException("boom"));

        consumer.consume(job);

        verify(events).publish(TranscodeEvent.failed(7L, 3, "boom", false));
        verify(events).deadLetter(job);
        verify(events, never()).scheduleRetry(any());
    }
}
