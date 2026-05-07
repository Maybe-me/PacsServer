package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.repository.PacsStudyRepository;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import com.mylife.pacs.infrastructure.persistence.entity.StudyEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.PatientEntityRepository;
import com.mylife.pacs.infrastructure.persistence.springdata.StudyEntityRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaPacsStudyRepository implements PacsStudyRepository {

    private final StudyEntityRepository repository;
    private final PatientEntityRepository patientRepository;

    public JpaPacsStudyRepository(StudyEntityRepository repository, PatientEntityRepository patientRepository) {
        this.repository = repository;
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PacsStudy> findByStudyInstanceUid(String studyInstanceUid) {
        return repository.findByStudyInstanceUid(studyInstanceUid).map(PersistenceMapper::toDomain);
    }

    @Override
    public PacsStudy save(PacsStudy study) {
        StudyEntity entity = study.id() == null
                ? new StudyEntity()
                : repository.findById(study.id()).orElseGet(StudyEntity::new);
        entity.setId(study.id());
        entity.setPatient(patientRepository.getReferenceById(study.patientFk()));
        entity.setStudyInstanceUid(study.studyInstanceUid());
        entity.setAccessionNo(study.accessionNo());
        entity.setStudyDate(study.studyDate());
        entity.setStudyTime(study.studyTime());
        entity.setStudyDescription(study.studyDescription());
        entity.setModalitiesInStudy(study.modalitiesInStudy());
        entity.setReferringDoctor(study.referringDoctor());
        entity.setNumSeries(study.numSeries());
        entity.setNumInstances(study.numInstances());
        entity.setExtraTagsJson(JsonUtil.writeMap(study.extraTags()));
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacsStudy> search(StudyQueryCriteria criteria) {
        Specification<StudyEntity> specification = Specification.where(equalsIfPresent("studyInstanceUid", criteria.studyInstanceUid()))
                .and(equalsIfPresent("accessionNo", criteria.accessionNo()))
                .and(likeIfPresent("modalitiesInStudy", criteria.modality()))
                .and(greaterThanOrEqualIfPresent("studyDate", criteria.studyDateFrom()))
                .and(lessThanOrEqualIfPresent("studyDate", criteria.studyDateTo()))
                .and(patientFieldEqualsIfPresent("patientId", criteria.patientId()))
                .and(patientFieldEqualsIfPresent("issuerOfPatientId", criteria.issuerOfPatientId() == null ? null : criteria.issuerOfPatientId()));
        return repository.findAll(specification).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    private Specification<StudyEntity> equalsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank() ? null : builder.equal(root.get(field), value);
    }

    private Specification<StudyEntity> likeIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.like(root.get(field), "%" + value + "%");
    }

    private Specification<StudyEntity> greaterThanOrEqualIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.greaterThanOrEqualTo(root.get(field), value);
    }

    private Specification<StudyEntity> lessThanOrEqualIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.lessThanOrEqualTo(root.get(field), value);
    }

    private Specification<StudyEntity> patientFieldEqualsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.equal(root.join("patient").get(field), value);
    }
}
