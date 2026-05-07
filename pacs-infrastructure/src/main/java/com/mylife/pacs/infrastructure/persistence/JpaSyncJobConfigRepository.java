package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.domain.repository.SyncJobConfigRepository;
import com.mylife.pacs.infrastructure.persistence.entity.SyncJobConfigEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.SyncJobConfigEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaSyncJobConfigRepository implements SyncJobConfigRepository {

    private final SyncJobConfigEntityRepository repository;

    public JpaSyncJobConfigRepository(SyncJobConfigEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByJobName(String jobName) {
        return repository.existsByJobName(jobName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SyncJobConfig> findByJobName(String jobName) {
        return repository.findByJobName(jobName).map(PersistenceMapper::toDomain);
    }

    @Override
    public SyncJobConfig save(SyncJobConfig jobConfig) {
        SyncJobConfigEntity entity = jobConfig.id() == null
                ? new SyncJobConfigEntity()
                : repository.findById(jobConfig.id()).orElseGet(SyncJobConfigEntity::new);
        entity.setId(jobConfig.id());
        entity.setJobName(jobConfig.jobName());
        entity.setJobType(jobConfig.jobType());
        entity.setTargetAet(jobConfig.targetAet());
        entity.setDestinationAet(jobConfig.destinationAet());
        entity.setPatientId(jobConfig.patientId());
        entity.setModality(jobConfig.modality());
        entity.setStudyDateLookbackDays(jobConfig.studyDateLookbackDays());
        entity.setPreventLoopToSource(Boolean.TRUE.equals(jobConfig.preventLoopToSource()));
        entity.setSkipRemoteDuplicates(Boolean.TRUE.equals(jobConfig.skipRemoteDuplicates()));
        entity.setMaxStudiesPerRun(jobConfig.maxStudiesPerRun() == null ? 0 : jobConfig.maxStudiesPerRun());
        entity.setMaxInstancesPerRun(jobConfig.maxInstancesPerRun() == null ? 0 : jobConfig.maxInstancesPerRun());
        entity.setThrottleDelayMs(jobConfig.throttleDelayMs() == null ? 0L : jobConfig.throttleDelayMs());
        entity.setSourceAetAllowList(JsonUtil.writeList(jobConfig.sourceAetAllowList()));
        entity.setSourceAetBlockList(JsonUtil.writeList(jobConfig.sourceAetBlockList()));
        entity.setMaxRetryCount(jobConfig.maxRetryCount() == null ? 0 : jobConfig.maxRetryCount());
        entity.setFailureThreshold(jobConfig.failureThreshold() == null ? 3 : jobConfig.failureThreshold());
        entity.setPaused(Boolean.TRUE.equals(jobConfig.paused()));
        entity.setConsecutiveFailureCount(jobConfig.consecutiveFailureCount() == null ? 0 : jobConfig.consecutiveFailureCount());
        entity.setLastErrorCategory(jobConfig.lastErrorCategory());
        entity.setLastErrorMessage(jobConfig.lastErrorMessage());
        entity.setLastSuccessAt(jobConfig.lastSuccessAt());
        entity.setLastFailureAt(jobConfig.lastFailureAt());
        entity.setEnabled(jobConfig.enabled());
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncJobConfig> findAll() {
        return repository.findAllByOrderByJobNameAsc().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncJobConfig> findEnabled() {
        return repository.findByEnabledTrueOrderByJobNameAsc().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public void deleteByJobName(String jobName) {
        repository.deleteByJobName(jobName);
    }
}
