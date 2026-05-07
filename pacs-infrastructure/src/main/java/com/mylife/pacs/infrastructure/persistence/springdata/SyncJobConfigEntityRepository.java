package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.SyncJobConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncJobConfigEntityRepository extends JpaRepository<SyncJobConfigEntity, Long> {

    boolean existsByJobName(String jobName);

    Optional<SyncJobConfigEntity> findByJobName(String jobName);

    List<SyncJobConfigEntity> findByEnabledTrueOrderByJobNameAsc();

    List<SyncJobConfigEntity> findAllByOrderByJobNameAsc();

    void deleteByJobName(String jobName);
}
