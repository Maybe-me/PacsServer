package com.mylife.pacs.domain.model;

import java.time.Instant;
import java.util.Map;

public record PacsSeries(
        Long id,
        Long studyFk,
        String seriesInstanceUid,
        String modality,
        String seriesDescription,
        String bodyPartExamined,
        Integer seriesNumber,
        int numInstances,
        Map<String, String> extraTags,
        Instant createdAt,
        Instant updatedAt
) {
}
