package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.AetNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AetNodeEntityRepository extends JpaRepository<AetNodeEntity, Long> {

    boolean existsByAet(String aet);

    Optional<AetNodeEntity> findByAet(String aet);

    List<AetNodeEntity> findByEnabledTrue();

    void deleteByAet(String aet);
}
