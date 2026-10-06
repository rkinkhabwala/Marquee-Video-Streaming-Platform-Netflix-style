package com.marquee.transcoder.ffmpeg;

import java.util.ArrayList;
import java.util.List;

public class FfmpegCommandBuilder {
    private static final int KEYFRAME_INTERVAL = 48;

    public List<String> buildRenditionCommand(String inputPath, String outputDir, Rendition rendition, int sourceFps) {
        int keyframeInterval = Math.max(KEYFRAME_INTERVAL, sourceFps);
        int fpsArgument = sourceFps > 0 ? sourceFps : 24;

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputPath);
        cmd.add("-vf");
        cmd.add("scale=w=%d:h=%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2".formatted(
                rendition.width(), rendition.height(), rendition.width(), rendition.height()));
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-crf");
        cmd.add("23");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-g");
        cmd.add(String.valueOf(keyframeInterval));
        cmd.add("-keyint_min");
        cmd.add(String.valueOf(keyframeInterval));
        cmd.add("-sc_threshold");
        cmd.add("0");
        cmd.add("-r");
        cmd.add(String.valueOf(fpsArgument));
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-ar");
        cmd.add("48000");
        cmd.add("-b:a");
        cmd.add(rendition.audioBitrate());
        cmd.add("-b:v");
        cmd.add(rendition.videoBitrate());
        cmd.add("-hls_time");
        cmd.add("6");
        cmd.add("-hls_playlist_type");
        cmd.add("vod");
        cmd.add("-hls_segment_filename");
        cmd.add(outputDir + "/seg_%03d.ts");
        cmd.add(outputDir + "/index.m3u8");
        return cmd;
    }

    public List<String> buildMasterPlaylist(String outputDir, List<Rendition> renditions) {
        List<String> lines = new ArrayList<>();
        lines.add("#EXTM3U");
        lines.add("#EXT-X-VERSION:3");
        for (Rendition rendition : renditions) {
            int bandwidth = estimateBandwidth(rendition);
            lines.add("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d".formatted(
                    bandwidth, rendition.width(), rendition.height()));
            lines.add("%s/%s/index.m3u8".formatted(outputDir, rendition.width() + "p"));
        }
        return lines;
    }

    private int estimateBandwidth(Rendition rendition) {
        return switch (rendition.width()) {
            case 1920 -> 5_000_000;
            case 1280 -> 2_800_000;
            case 854 -> 1_400_000;
            case 640 -> 800_000;
            default -> 1_000_000;
        };
    }
}
