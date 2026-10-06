package com.marquee.api.storage;

import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseInputStream;

public interface ObjectStorageService {
    PresignedUploadResponse presignImageUpload(ImageUploadRequest request);

    PresignedUploadResponse presignUpload(String objectKey, String contentType);

    boolean objectExists(String objectKey);

    ResponseInputStream<GetObjectResponse> getObject(String objectKey, String rangeHeader);

    byte[] getObjectBytes(String objectKey, String rangeHeader);
}
