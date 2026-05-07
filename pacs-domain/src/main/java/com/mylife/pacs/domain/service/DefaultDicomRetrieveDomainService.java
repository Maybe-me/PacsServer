package com.mylife.pacs.domain.service;

import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.repository.PacsInstanceRepository;

import java.util.List;

public class DefaultDicomRetrieveDomainService implements DicomRetrieveDomainService {

    private final PacsInstanceRepository instanceRepository;

    public DefaultDicomRetrieveDomainService(PacsInstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    @Override
    public List<PacsInstance> findInstances(InstanceQueryCriteria criteria) {
        return instanceRepository.search(criteria);
    }
}
