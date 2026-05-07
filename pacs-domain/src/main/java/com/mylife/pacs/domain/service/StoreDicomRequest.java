package com.mylife.pacs.domain.service;

import java.util.Collections;
import java.util.Map;

public record StoreDicomRequest(
        String patientId,
        String issuerOfPatientId,
        String patientName,
        String patientSex,
        String patientBirthDate,
        Map<String, String> patientExtraTags,
        String studyInstanceUid,
        String accessionNo,
        String studyDate,
        String studyTime,
        String studyDescription,
        String modalitiesInStudy,
        String referringDoctor,
        Map<String, String> studyExtraTags,
        String seriesInstanceUid,
        String modality,
        String seriesDescription,
        String bodyPartExamined,
        Integer seriesNumber,
        Map<String, String> seriesExtraTags,
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
        Map<String, String> instanceExtraTags
) {
    public StoreDicomRequest {
        patientExtraTags = patientExtraTags == null ? Collections.emptyMap() : Map.copyOf(patientExtraTags);
        studyExtraTags = studyExtraTags == null ? Collections.emptyMap() : Map.copyOf(studyExtraTags);
        seriesExtraTags = seriesExtraTags == null ? Collections.emptyMap() : Map.copyOf(seriesExtraTags);
        instanceExtraTags = instanceExtraTags == null ? Collections.emptyMap() : Map.copyOf(instanceExtraTags);
    }
}
