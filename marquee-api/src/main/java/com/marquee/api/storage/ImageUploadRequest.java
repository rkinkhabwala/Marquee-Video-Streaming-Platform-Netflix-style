package com.marquee.api.storage;

import jakarta.validation.constraints.NotBlank;

public record ImageUploadRequest(
        @NotBlank String kind,
        @NotBlank String fileName,
        Long titleId) {
}
