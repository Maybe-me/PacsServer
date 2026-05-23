package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.repository.PacsPatientRepository;
import com.mylife.pacs.domain.service.PatientQueryCriteria;
import com.mylife.pacs.infrastructure.persistence.entity.PatientEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.PatientEntityRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaPacsPatientRepository implements PacsPatientRepository {

    private final PatientEntityRepository repository;

    public JpaPacsPatientRepository(PatientEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PacsPatient> findByPatientKey(String patientId, String issuerOfPatientId) {
        return repository.findByPatientIdAndIssuerOfPatientId(patientId, normalizeIssuer(issuerOfPatientId))
                .map(PersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PacsPatient> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public PacsPatient save(PacsPatient patient) {
        PatientEntity entity = patient.id() == null
                ? new PatientEntity()
                : repository.findById(patient.id()).orElseGet(PatientEntity::new);
        entity.setId(patient.id());
        entity.setPatientId(patient.patientId());
        entity.setIssuerOfPatientId(normalizeIssuer(patient.issuerOfPatientId()));
        entity.setPatientName(patient.patientName());
        entity.setPatientSex(patient.patientSex());
        entity.setPatientBirthDate(patient.patientBirthDate());
        entity.setExtraTagsJson(JsonUtil.writeMap(patient.extraTags()));
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacsPatient> search(PatientQueryCriteria criteria) {
        Specification<PatientEntity> specification = Specification.where(equalsIfPresent("patientId", criteria.patientId()))
                .and(equalsIfPresent("issuerOfPatientId", normalizeIssuer(criteria.issuerOfPatientId())))
                .and(likeIfPresent("patientName", criteria.patientName()));
        return repository.findAll(specification).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    private Specification<PatientEntity> equalsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank() ? null : builder.equal(root.get(field), value);
    }

    private Specification<PatientEntity> likeIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.like(root.get(field), "%" + value + "%");
    }

    private String normalizeIssuer(String issuer) {
        return issuer == null ? "" : issuer;
    }
}
