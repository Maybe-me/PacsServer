package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.SeriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SeriesEntityRepository extends JpaRepository<SeriesEntity, Long>, JpaSpecificationExecutor<SeriesEntity> {

    Optional<SeriesEntity> findBySeriesInstanceUid(String seriesInstanceUid);
}
