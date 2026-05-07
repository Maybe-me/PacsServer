package com.mylife.pacs.domain.repository;

import com.mylife.pacs.domain.model.SyncJobConfig;

import java.util.List;
import java.util.Optional;

public interface SyncJobConfigRepository {

    boolean existsByJobName(String jobName);

    Optional<SyncJobConfig> findByJobName(String jobName);

    SyncJobConfig save(SyncJobConfig jobConfig);

    List<SyncJobConfig> findAll();

    List<SyncJobConfig> findEnabled();

    void deleteByJobName(String jobName);
}
