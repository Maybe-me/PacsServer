package com.mylife.pacs.domain.model;

import java.time.Instant;
import java.util.Map;

public record PacsStudy(
        Long id,
        Long patientFk,
        String studyInstanceUid,
        String accessionNo,
        String studyDate,
        String studyTime,
        String studyDescription,
        String modalitiesInStudy,
        String referringDoctor,
        int numSeries,
        int numInstances,
        Map<String, String> extraTags,
        Instant createdAt,
        Instant updatedAt
) {
}
