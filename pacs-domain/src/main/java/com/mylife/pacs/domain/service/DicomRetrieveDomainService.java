package com.mylife.pacs.domain.service;

import com.mylife.pacs.domain.model.PacsInstance;

import java.util.List;

public interface DicomRetrieveDomainService {

    List<PacsInstance> findInstances(InstanceQueryCriteria criteria);
}
