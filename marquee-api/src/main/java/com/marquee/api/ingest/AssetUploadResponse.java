package com.marquee.api.ingest;

public record AssetUploadResponse(
        Long assetId,
        Long titleId,
        String sourceKey,
        String uploadUrl,
        String contentType,
        String status) {
}
