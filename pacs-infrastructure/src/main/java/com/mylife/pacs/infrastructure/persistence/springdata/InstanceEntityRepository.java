package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.InstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InstanceEntityRepository extends JpaRepository<InstanceEntity, Long>, JpaSpecificationExecutor<InstanceEntity> {

    Optional<InstanceEntity> findBySopInstanceUid(String sopInstanceUid);
}
