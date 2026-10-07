package com.marquee.transcoder.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface TranscodeStorageService {
    InputStream download(String objectKey) throws IOException;

    boolean exists(String objectKey);

    void uploadDirectory(Path directory, String destinationPrefix) throws IOException;

    void uploadFile(Path sourceFile, String destinationKey) throws IOException;
}
