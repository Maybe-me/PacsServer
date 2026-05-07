package com.mylife.pacs.domain.service;

import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;

public record StoreResult(
        PacsPatient patient,
        PacsStudy study,
        PacsSeries series,
        PacsInstance instance
) {
}
