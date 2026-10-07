package com.marquee.api.storage;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Serves title artwork from the private bucket so browsers can load {@code posterKey}/{@code backdropKey}
 * as {@code /media/<key>}. Only the {@code images/{titleId}/poster|backdrop.<ext>} layout is exposed.
 */
@RestController
public class MediaController {
    private static final Pattern IMAGE_FILE = Pattern.compile("(poster|backdrop)\\.(jpg|png|webp)");

    private final ObjectStorageService objectStorageService;

    public MediaController(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @GetMapping("/media/images/{titleId}/{file}")
    public ResponseEntity<StreamingResponseBody> image(@PathVariable Long titleId, @PathVariable String file) {
        if (!IMAGE_FILE.matcher(file).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        ResponseInputStream<GetObjectResponse> stream;
        try {
            stream = objectStorageService.getObject("images/" + titleId + "/" + file, null);
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        GetObjectResponse object = stream.response();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType() == null ? "image/jpeg" : object.contentType()))
                // Re-uploading artwork overwrites the same key, so keep the cache short-lived.
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        if (object.contentLength() != null) {
            response.contentLength(object.contentLength());
        }
        return response.body(out -> {
            try (stream) {
                stream.transferTo(out);
            }
        });
    }
}
