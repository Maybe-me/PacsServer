package com.mylife.pacs.domain.service;

public record StudyQueryCriteria(
        String patientId,
        String issuerOfPatientId,
        String studyInstanceUid,
        String accessionNo,
        String modality,
        String studyDateFrom,
        String studyDateTo
) {
}
