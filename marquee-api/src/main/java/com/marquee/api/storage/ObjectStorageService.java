package com.marquee.api.storage;

public interface ObjectStorageService {
    PresignedUploadResponse presignImageUpload(ImageUploadRequest request);
}
