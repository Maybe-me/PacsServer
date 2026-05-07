package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.SyncExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncExecutionEntityRepository extends JpaRepository<SyncExecutionEntity, Long> {

    List<SyncExecutionEntity> findTop50ByOrderByStartedAtDesc();

    List<SyncExecutionEntity> findTop200ByOrderByStartedAtDesc();
}
