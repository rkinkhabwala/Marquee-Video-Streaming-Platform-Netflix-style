package com.marquee.transcoder.ffmpeg;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FfmpegCommandBuilder {
    private static final int SEGMENT_SECONDS = 6;
    private static final int THUMBNAIL_INTERVAL_SECONDS = 10;

    public List<String> buildRenditionCommand(String inputPath, String outputDir, Rendition rendition, int sourceFps) {
        int fps = sourceFps > 0 ? sourceFps : 24;
        // Two-second GOPs keep keyframes aligned across renditions and on segment boundaries.
        int keyframeInterval = fps * 2;
        int videoKbps = Integer.parseInt(rendition.videoBitrate().replace("k", ""));

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
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-b:v");
        cmd.add(rendition.videoBitrate());
        cmd.add("-maxrate");
        cmd.add(rendition.videoBitrate());
        cmd.add("-bufsize");
        cmd.add((videoKbps * 2) + "k");
        cmd.add("-r");
        cmd.add(String.valueOf(fps));
        cmd.add("-g");
        cmd.add(String.valueOf(keyframeInterval));
        cmd.add("-keyint_min");
        cmd.add(String.valueOf(keyframeInterval));
        cmd.add("-sc_threshold");
        cmd.add("0");
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-ar");
        cmd.add("48000");
        cmd.add("-b:a");
        cmd.add(rendition.audioBitrate());
        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add(String.valueOf(SEGMENT_SECONDS));
        cmd.add("-hls_playlist_type");
        cmd.add("vod");
        cmd.add("-hls_segment_filename");
        cmd.add(outputDir + "/seg_%05d.ts");
        cmd.add(outputDir + "/index.m3u8");
        return cmd;
    }

    public List<String> buildThumbnailCommand(String inputPath, String outputDir) {
        return List.of(
                "ffmpeg",
                "-y",
                "-i",
                inputPath,
                "-vf",
                "fps=1/%d,scale=320:-2".formatted(THUMBNAIL_INTERVAL_SECONDS),
                "-q:v",
                "5",
                outputDir + "/thumb_%d.jpg");
    }

    /** Master playlist with variant URIs relative to the master, best quality first. */
    public List<String> buildMasterPlaylist(List<Rendition> renditions) {
        List<String> lines = new ArrayList<>();
        lines.add("#EXTM3U");
        lines.add("#EXT-X-VERSION:3");
        for (Rendition rendition : renditions) {
            lines.add("#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d".formatted(
                    rendition.bandwidth(), rendition.width(), rendition.height()));
            lines.add(rendition.name() + "/index.m3u8");
        }
        return lines;
    }

    /** Ladder rungs at or below the source height; always at least the lowest rung. */
    public List<Rendition> selectRenditions(int sourceHeight) {
        List<Rendition> selected = Rendition.LADDER.stream()
                .filter(rendition -> rendition.height() <= sourceHeight)
                .toList();
        return selected.isEmpty() ? List.of(Rendition.P360) : selected;
    }
}
