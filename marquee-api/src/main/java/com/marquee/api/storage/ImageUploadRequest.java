package com.marquee.api.storage;

import jakarta.validation.constraints.NotBlank;

/** {@code titleId} comes from the request path, not the body. */
public record ImageUploadRequest(
        @NotBlank String kind,
        @NotBlank String fileName,
        Long titleId) {
}
