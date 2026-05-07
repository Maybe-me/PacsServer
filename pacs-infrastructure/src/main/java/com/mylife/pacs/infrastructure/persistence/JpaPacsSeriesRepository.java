package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.repository.PacsSeriesRepository;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.infrastructure.persistence.entity.SeriesEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.SeriesEntityRepository;
import com.mylife.pacs.infrastructure.persistence.springdata.StudyEntityRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaPacsSeriesRepository implements PacsSeriesRepository {

    private final SeriesEntityRepository repository;
    private final StudyEntityRepository studyRepository;

    public JpaPacsSeriesRepository(SeriesEntityRepository repository, StudyEntityRepository studyRepository) {
        this.repository = repository;
        this.studyRepository = studyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PacsSeries> findBySeriesInstanceUid(String seriesInstanceUid) {
        return repository.findBySeriesInstanceUid(seriesInstanceUid).map(PersistenceMapper::toDomain);
    }

    @Override
    public PacsSeries save(PacsSeries series) {
        SeriesEntity entity = series.id() == null
                ? new SeriesEntity()
                : repository.findById(series.id()).orElseGet(SeriesEntity::new);
        entity.setId(series.id());
        entity.setStudy(studyRepository.getReferenceById(series.studyFk()));
        entity.setSeriesInstanceUid(series.seriesInstanceUid());
        entity.setModality(series.modality());
        entity.setSeriesDescription(series.seriesDescription());
        entity.setBodyPartExamined(series.bodyPartExamined());
        entity.setSeriesNumber(series.seriesNumber());
        entity.setNumInstances(series.numInstances());
        entity.setExtraTagsJson(JsonUtil.writeMap(series.extraTags()));
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacsSeries> search(SeriesQueryCriteria criteria) {
        Specification<SeriesEntity> specification = Specification.where(equalsIfPresent("seriesInstanceUid", criteria.seriesInstanceUid()))
                .and(equalsIfPresent("modality", criteria.modality()))
                .and(studyFieldEqualsIfPresent("studyInstanceUid", criteria.studyInstanceUid()));
        return repository.findAll(specification).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    private Specification<SeriesEntity> equalsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank() ? null : builder.equal(root.get(field), value);
    }

    private Specification<SeriesEntity> studyFieldEqualsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.equal(root.join("study").get(field), value);
    }
}
