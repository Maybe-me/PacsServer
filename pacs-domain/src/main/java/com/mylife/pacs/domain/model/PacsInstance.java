package com.mylife.pacs.domain.model;

import java.time.Instant;
import java.util.Map;

public record PacsInstance(
        Long id,
        Long seriesFk,
        String sopInstanceUid,
        String sopClassUid,
        String transferSyntaxUid,
        Integer instanceNumber,
        String filePath,
        Long fileSize,
        String fileMd5,
        String storageType,
        String storageBucket,
        String storageKey,
        Map<String, String> extraTags,
        Instant createdAt,
        Instant updatedAt
) {
}
