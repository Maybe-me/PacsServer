package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.PatientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PatientEntityRepository extends JpaRepository<PatientEntity, Long>, JpaSpecificationExecutor<PatientEntity> {

    Optional<PatientEntity> findByPatientIdAndIssuerOfPatientId(String patientId, String issuerOfPatientId);
}
