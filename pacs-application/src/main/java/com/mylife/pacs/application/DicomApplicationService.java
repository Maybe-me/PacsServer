package com.mylife.pacs.application;

import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.DicomQueryDomainService;
import com.mylife.pacs.domain.service.DicomRetrieveDomainService;
import com.mylife.pacs.domain.service.DicomStoreDomainService;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.PatientQueryCriteria;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.domain.service.StoreDicomRequest;
import com.mylife.pacs.domain.service.StoreResult;
import com.mylife.pacs.domain.service.StudyQueryCriteria;

import java.util.List;

public class DicomApplicationService {

    private final DicomStoreDomainService storeDomainService;
    private final DicomQueryDomainService queryDomainService;
    private final DicomRetrieveDomainService retrieveDomainService;

    public DicomApplicationService(
            DicomStoreDomainService storeDomainService,
            DicomQueryDomainService queryDomainService,
            DicomRetrieveDomainService retrieveDomainService
    ) {
        this.storeDomainService = storeDomainService;
        this.queryDomainService = queryDomainService;
        this.retrieveDomainService = retrieveDomainService;
    }

    public StoreResult store(StoreDicomRequest request) {
        return storeDomainService.store(request);
    }

    public List<PacsPatient> findPatients(PatientQueryCriteria criteria) {
        return queryDomainService.searchPatients(criteria);
    }

    public List<PacsStudy> findStudies(StudyQueryCriteria criteria) {
        return queryDomainService.searchStudies(criteria);
    }

    public List<PacsSeries> findSeries(SeriesQueryCriteria criteria) {
        return queryDomainService.searchSeries(criteria);
    }

    public List<PacsInstance> findInstances(InstanceQueryCriteria criteria) {
        return retrieveDomainService.findInstances(criteria);
    }
}
