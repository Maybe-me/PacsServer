package com.mylife.pacs.application;

import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;

import java.util.List;

public class ViewerApplicationService {

    private final DicomApplicationService dicomApplicationService;

    public ViewerApplicationService(DicomApplicationService dicomApplicationService) {
        this.dicomApplicationService = dicomApplicationService;
    }

    public List<PacsStudy> loadStudies(StudyQueryCriteria criteria) {
        return dicomApplicationService.findStudies(criteria);
    }

    public List<PacsSeries> loadSeries(SeriesQueryCriteria criteria) {
        return dicomApplicationService.findSeries(criteria);
    }

    public List<PacsInstance> loadInstances(InstanceQueryCriteria criteria) {
        return dicomApplicationService.findInstances(criteria);
    }
}
