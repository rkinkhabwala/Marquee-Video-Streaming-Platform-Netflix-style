package com.marquee.transcoder.ffmpeg;

public record Rendition(int width, int height, String videoBitrate, String audioBitrate) {
    public static final Rendition P1080 = new Rendition(1920, 1080, "5000k", "128k");
    public static final Rendition P720 = new Rendition(1280, 720, "2800k", "128k");
    public static final Rendition P480 = new Rendition(854, 480, "1400k", "96k");
    public static final Rendition P360 = new Rendition(640, 360, "800k", "96k");

    public static final java.util.List<Rendition> LADDER = java.util.List.of(P1080, P720, P480, P360);

    /** Directory and playlist name, e.g. {@code 720p}. */
    public String name() {
        return height + "p";
    }

    public int bandwidth() {
        return (parseKbps(videoBitrate) + parseKbps(audioBitrate)) * 1000;
    }

    private static int parseKbps(String bitrate) {
        return Integer.parseInt(bitrate.substring(0, bitrate.length() - 1));
    }
}
