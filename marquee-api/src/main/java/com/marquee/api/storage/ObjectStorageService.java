package com.marquee.api.storage;

public interface ObjectStorageService {
    PresignedUploadResponse presignImageUpload(ImageUploadRequest request);

    PresignedUploadResponse presignUpload(String objectKey, String contentType);

    boolean objectExists(String objectKey);
}
