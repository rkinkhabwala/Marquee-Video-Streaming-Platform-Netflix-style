package com.marquee.transcoder.service;

import org.springframework.stereotype.Component;

@Component
public class HttpTranscodeStatusCallback implements TranscodeStatusCallback {
    @Override
    public void onSuccess(Long assetId, String masterPlaylistKey) {
        // Intended to call the API status endpoint during a full integration implementation.
    }

    @Override
    public void onFailure(Long assetId, String errorMessage) {
        // Intended to call the API status endpoint during a full integration implementation.
    }
}
