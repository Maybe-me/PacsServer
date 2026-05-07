package com.mylife.pacs.domain.repository;

import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.StudyQueryCriteria;

import java.util.List;
import java.util.Optional;

public interface PacsStudyRepository {

    Optional<PacsStudy> findByStudyInstanceUid(String studyInstanceUid);

    PacsStudy save(PacsStudy study);

    List<PacsStudy> search(StudyQueryCriteria criteria);
}
