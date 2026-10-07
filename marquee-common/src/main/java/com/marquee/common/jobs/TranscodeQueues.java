package com.marquee.common.jobs;

/** RabbitMQ queue names shared by the API and the transcoder (all on the default exchange). */
public final class TranscodeQueues {
    /** Jobs waiting to be transcoded. */
    public static final String JOBS = "marquee.transcode";
    /** Failed jobs wait here for a TTL, then dead-letter back to {@link #JOBS}. */
    public static final String RETRY = "marquee.transcode.retry";
    /** Jobs that exhausted all attempts. */
    public static final String DEAD_LETTER = "marquee.transcode.dlq";
    /** Status events back to the API. */
    public static final String EVENTS = "marquee.transcode.events";

    private TranscodeQueues() {
    }
}
