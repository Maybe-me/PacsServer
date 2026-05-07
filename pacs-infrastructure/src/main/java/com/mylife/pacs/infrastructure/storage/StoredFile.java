package com.mylife.pacs.infrastructure.storage;

import java.nio.file.Path;

public record StoredFile(
        Path absolutePath,
        String relativePath,
        String storageType,
        String storageBucket,
        String storageKey,
        long fileSize,
        String fileMd5
) {
}
