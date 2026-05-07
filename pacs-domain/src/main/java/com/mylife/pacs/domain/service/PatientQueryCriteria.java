package com.mylife.pacs.domain.service;

public record PatientQueryCriteria(
        String patientId,
        String issuerOfPatientId,
        String patientName
) {
}
