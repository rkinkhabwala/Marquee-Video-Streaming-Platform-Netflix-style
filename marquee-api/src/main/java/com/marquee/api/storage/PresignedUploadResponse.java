package com.marquee.api.storage;

public record PresignedUploadResponse(String objectKey, String uploadUrl, String contentType) {
}
