package com.marquee.api.ingest;

public record AssetStatusResponse(Long assetId, Long titleId, String status, String sourceKey) {
}
