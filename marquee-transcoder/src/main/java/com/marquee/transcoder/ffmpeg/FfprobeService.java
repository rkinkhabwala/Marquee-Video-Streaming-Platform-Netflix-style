package com.marquee.transcoder.ffmpeg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FfprobeService {
    public ProbeMetadata probe(String inputPath) {
        List<String> command = List.of(
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height,avg_frame_rate,duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                inputPath);

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right);
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("ffprobe failed for %s: %s".formatted(inputPath, output));
            }
            String[] tokens = output.split("\\R");
            int width = Integer.parseInt(tokens[0]);
            int height = Integer.parseInt(tokens[1]);
            int fps = 24;
            if (tokens.length > 2 && !tokens[2].isBlank()) {
                String frameRateText = tokens[2];
                int slash = frameRateText.indexOf('/');
                if (slash > 0) {
                    fps = Integer.parseInt(frameRateText.substring(0, slash));
                }
            }
            return new ProbeMetadata(width, height, fps);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to probe input video: " + inputPath, e);
        }
    }

    public record ProbeMetadata(int width, int height, int fps) {
    }
}
