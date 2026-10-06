package com.marquee.transcoder.ffmpeg;

public record Rendition(int width, int height, String videoBitrate, String audioBitrate) {
    public static final Rendition P1080 = new Rendition(1920, 1080, "5000k", "128k");
    public static final Rendition P720 = new Rendition(1280, 720, "2800k", "128k");
    public static final Rendition P480 = new Rendition(854, 480, "1400k", "96k");
    public static final Rendition P360 = new Rendition(640, 360, "800k", "96k");
}
