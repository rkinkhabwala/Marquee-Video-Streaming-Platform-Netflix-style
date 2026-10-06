package com.marquee.transcoder.service;

public interface TranscodeStatusCallback {
    void onSuccess(Long assetId, String masterPlaylistKey);
    void onFailure(Long assetId, String errorMessage);
}
