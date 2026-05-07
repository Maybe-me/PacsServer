package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.repository.PacsInstanceRepository;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.persistence.entity.InstanceEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.InstanceEntityRepository;
import com.mylife.pacs.infrastructure.persistence.springdata.SeriesEntityRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaPacsInstanceRepository implements PacsInstanceRepository {

    private final InstanceEntityRepository repository;
    private final SeriesEntityRepository seriesRepository;

    public JpaPacsInstanceRepository(InstanceEntityRepository repository, SeriesEntityRepository seriesRepository) {
        this.repository = repository;
        this.seriesRepository = seriesRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PacsInstance> findBySopInstanceUid(String sopInstanceUid) {
        return repository.findBySopInstanceUid(sopInstanceUid).map(PersistenceMapper::toDomain);
    }

    @Override
    public PacsInstance save(PacsInstance instance) {
        InstanceEntity entity = instance.id() == null
                ? new InstanceEntity()
                : repository.findById(instance.id()).orElseGet(InstanceEntity::new);
        entity.setId(instance.id());
        entity.setSeries(seriesRepository.getReferenceById(instance.seriesFk()));
        entity.setSopInstanceUid(instance.sopInstanceUid());
        entity.setSopClassUid(instance.sopClassUid());
        entity.setTransferSyntaxUid(instance.transferSyntaxUid());
        entity.setInstanceNumber(instance.instanceNumber());
        entity.setFilePath(instance.filePath());
        entity.setFileSize(instance.fileSize());
        entity.setFileMd5(instance.fileMd5());
        entity.setStorageType(instance.storageType());
        entity.setStorageBucket(instance.storageBucket());
        entity.setStorageKey(instance.storageKey());
        entity.setExtraTagsJson(JsonUtil.writeMap(instance.extraTags()));
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacsInstance> search(InstanceQueryCriteria criteria) {
        Specification<InstanceEntity> specification = Specification.where(equalsIfPresent("sopInstanceUid", criteria.sopInstanceUid()))
                .and(seriesFieldEqualsIfPresent("seriesInstanceUid", criteria.seriesInstanceUid()))
                .and(studyFieldEqualsIfPresent("studyInstanceUid", criteria.studyInstanceUid()));
        return repository.findAll(specification).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    private Specification<InstanceEntity> equalsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank() ? null : builder.equal(root.get(field), value);
    }

    private Specification<InstanceEntity> seriesFieldEqualsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.equal(root.join("series").get(field), value);
    }

    private Specification<InstanceEntity> studyFieldEqualsIfPresent(String field, String value) {
        return (root, query, builder) -> value == null || value.isBlank()
                ? null
                : builder.equal(root.join("series").join("study").get(field), value);
    }
}
