package com.mylife.pacs.application;

import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.domain.repository.SyncJobConfigRepository;

import java.util.List;

public class SyncJobConfigApplicationService {

    private final SyncJobConfigRepository repository;

    public SyncJobConfigApplicationService(SyncJobConfigRepository repository) {
        this.repository = repository;
    }

    public SyncJobConfig create(SyncJobConfig jobConfig) {
        return repository.save(jobConfig);
    }

    public SyncJobConfig update(SyncJobConfig jobConfig) {
        return repository.save(jobConfig);
    }

    public SyncJobConfig findByJobName(String jobName) {
        return repository.findByJobName(jobName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown sync job: " + jobName));
    }

    public List<SyncJobConfig> listAll() {
        return repository.findAll();
    }

    public List<SyncJobConfig> listEnabled() {
        return repository.findEnabled();
    }

    public void remove(String jobName) {
        repository.deleteByJobName(jobName);
    }
}
