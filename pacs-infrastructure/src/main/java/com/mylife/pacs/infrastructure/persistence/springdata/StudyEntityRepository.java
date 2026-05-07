package com.mylife.pacs.infrastructure.persistence.springdata;

import com.mylife.pacs.infrastructure.persistence.entity.StudyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface StudyEntityRepository extends JpaRepository<StudyEntity, Long>, JpaSpecificationExecutor<StudyEntity> {

    Optional<StudyEntity> findByStudyInstanceUid(String studyInstanceUid);
}
