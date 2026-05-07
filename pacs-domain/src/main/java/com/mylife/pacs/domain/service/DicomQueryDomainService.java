package com.mylife.pacs.domain.service;

import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;

import java.util.List;

public interface DicomQueryDomainService {

    List<PacsPatient> searchPatients(PatientQueryCriteria criteria);

    List<PacsStudy> searchStudies(StudyQueryCriteria criteria);

    List<PacsSeries> searchSeries(SeriesQueryCriteria criteria);
}
