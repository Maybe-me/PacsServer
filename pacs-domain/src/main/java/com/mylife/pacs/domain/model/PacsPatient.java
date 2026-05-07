package com.mylife.pacs.domain.model;

import java.time.Instant;
import java.util.Map;

public record PacsPatient(
        Long id,
        String patientId,
        String issuerOfPatientId,
        String patientName,
        String patientSex,
        String patientBirthDate,
        Map<String, String> extraTags,
        Instant createdAt,
        Instant updatedAt
) {
}
