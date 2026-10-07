package com.marquee.api.playback;

import com.marquee.api.security.UserPrincipal;
import com.marquee.api.storage.ObjectStorageService;
import com.marquee.common.storage.StorageKeys;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Controller
public class PlaybackController {
    private final PlaybackService playbackService;
    private final ObjectStorageService objectStorageService;

    public PlaybackController(PlaybackService playbackService, ObjectStorageService objectStorageService) {
        this.playbackService = playbackService;
        this.objectStorageService = objectStorageService;
    }

    @GetMapping("/api/playback/{assetId}")
    @ResponseBody
    public PlaybackResponse getPlayback(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestHeader(value = "X-Profile-Id", required = false) Long profileId,
                                       @PathVariable Long assetId) {
        if (profileId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Profile-Id header is required");
        }
        return playbackService.getPlayback(principal.getId(), profileId, assetId);
    }

    @GetMapping("/stream/{assetId}/{*path}")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable Long assetId,
                                                      @PathVariable String path,
                                                      @RequestParam(required = false) String token,
                                                      @RequestHeader(value = "Range", required = false) String rangeHeader) {
        playbackService.authorizeStream(assetId, token);
        String sanitizedPath = sanitizePath(path);
        String objectKey = StorageKeys.hlsPrefix(assetId) + sanitizedPath;

        try {
            if (sanitizedPath.toLowerCase(Locale.ROOT).endsWith(".m3u8")) {
                // Playlists are small and rewritten, so they are always served whole.
                byte[] bytes = objectStorageService.getObjectBytes(objectKey, null);
                String rewritten = rewriteManifest(new String(bytes, StandardCharsets.UTF_8), assetId, sanitizedPath, token);
                byte[] output = rewritten.getBytes(StandardCharsets.UTF_8);
                // An explicit length avoids chunked playlists, which ffmpeg's HLS demuxer reports as an I/O error at EOF.
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                        .contentLength(output.length)
                        .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                        .body(outputStream -> outputStream.write(output));
            }

            ResponseInputStream<GetObjectResponse> stream = objectStorageService.getObject(objectKey, rangeHeader);
            GetObjectResponse object = stream.response();
            ResponseEntity.BodyBuilder response = ResponseEntity
                    .status(object.contentRange() != null ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .contentType(MediaType.parseMediaType(inferContentType(sanitizedPath, object.contentType())))
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes");
            if (object.contentLength() != null) {
                response.contentLength(object.contentLength());
            }
            if (object.contentRange() != null) {
                response.header(HttpHeaders.CONTENT_RANGE, object.contentRange());
            }
            return response.body(outputStream -> {
                try (ResponseInputStream<GetObjectResponse> inputStream = stream) {
                    inputStream.transferTo(outputStream);
                }
            });
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream object not found");
        } catch (S3Exception e) {
            if (e.statusCode() == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
                throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Range not satisfiable");
            }
            throw e;
        }
    }

    private String sanitizePath(String path) {
        String sanitized = path.replace('\\', '/');
        if (sanitized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stream path");
        }
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
    }

    /**
     * Rewrites relative child URIs (variant playlists, segments) into absolute stream URLs carrying
     * the token. URIs resolve against the playlist's own directory, e.g. {@code 720p/seg.ts}.
     */
    static String rewriteManifest(String playlist, Long assetId, String playlistPath, String token) {
        int slash = playlistPath.lastIndexOf('/');
        String directory = slash >= 0 ? playlistPath.substring(0, slash + 1) : "";
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        StringBuilder rewritten = new StringBuilder();
        for (String line : playlist.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.contains("://") || trimmed.startsWith("/")) {
                rewritten.append(line).append('\n');
                continue;
            }
            String suffix = trimmed.contains("?") ? "&token=" : "?token=";
            rewritten.append("/stream/").append(assetId).append('/').append(directory).append(trimmed)
                    .append(suffix).append(encodedToken).append('\n');
        }
        return rewritten.toString();
    }

    private String inferContentType(String path, String fallback) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (lower.endsWith(".ts")) {
            return "video/mp2t";
        }
        return fallback == null ? "application/octet-stream" : fallback;
    }
}
