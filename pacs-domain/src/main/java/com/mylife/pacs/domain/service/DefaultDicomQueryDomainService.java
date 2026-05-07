package com.mylife.pacs.domain.service;

import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.repository.PacsPatientRepository;
import com.mylife.pacs.domain.repository.PacsSeriesRepository;
import com.mylife.pacs.domain.repository.PacsStudyRepository;

import java.util.List;

public class DefaultDicomQueryDomainService implements DicomQueryDomainService {

    private final PacsPatientRepository patientRepository;
    private final PacsStudyRepository studyRepository;
    private final PacsSeriesRepository seriesRepository;

    public DefaultDicomQueryDomainService(
            PacsPatientRepository patientRepository,
            PacsStudyRepository studyRepository,
            PacsSeriesRepository seriesRepository
    ) {
        this.patientRepository = patientRepository;
        this.studyRepository = studyRepository;
        this.seriesRepository = seriesRepository;
    }

    @Override
    public List<PacsPatient> searchPatients(PatientQueryCriteria criteria) {
        return patientRepository.search(criteria);
    }

    @Override
    public List<PacsStudy> searchStudies(StudyQueryCriteria criteria) {
        return studyRepository.search(criteria);
    }

    @Override
    public List<PacsSeries> searchSeries(SeriesQueryCriteria criteria) {
        return seriesRepository.search(criteria);
    }
}
