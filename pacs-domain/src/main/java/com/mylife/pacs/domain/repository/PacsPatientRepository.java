package com.mylife.pacs.domain.repository;

import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.service.PatientQueryCriteria;

import java.util.List;
import java.util.Optional;

public interface PacsPatientRepository {

    Optional<PacsPatient> findByPatientKey(String patientId, String issuerOfPatientId);

    PacsPatient save(PacsPatient patient);

    List<PacsPatient> search(PatientQueryCriteria criteria);
}
